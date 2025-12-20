package com.buildanalyzer.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class BuildAnalyzerCliTest {

    // ---------------- helpers: capture stdout/stderr + trap System.exit ----------------

    private static final class ExitTrappedError extends Error {
        final int status;

        ExitTrappedError(int status) {
            super("System.exit(" + status + ") trapped");
            this.status = status;
        }
    }

    @SuppressWarnings({"removal", "deprecation"})
    private static final class NoExitSecurityManager extends SecurityManager {
        @Override
        public void checkExit(int status) {
            throw new ExitTrappedError(status);
        }

        @Override
        public void checkPermission(java.security.Permission perm) {
            // allow everything
        }

        @Override
        public void checkPermission(java.security.Permission perm, Object context) {
            // allow everything
        }
    }

    private record RunResult(int exitCode, String out, String err) {}

    @SuppressWarnings({"removal", "deprecation"})
    private static RunResult runMainExpectExit(String[] args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        SecurityManager originalSm = System.getSecurityManager();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));

            try {
                System.setSecurityManager(new NoExitSecurityManager());
            } catch (UnsupportedOperationException e) {
                fail("""
                     Current JDK forbids installing SecurityManager, so we cannot trap System.exit.
                     If you're on JDK 18+/21+, add this to your test JVM:
                       -Djava.security.manager=allow
                     """);
            }

            try {
                BuildAnalyzerCli.main(args);
                fail("Expected System.exit(...) but the program returned normally.");
                return null; // unreachable
            } catch (ExitTrappedError e) {
                return new RunResult(
                        e.status,
                        out.toString(StandardCharsets.UTF_8),
                        err.toString(StandardCharsets.UTF_8)
                );
            }
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
            try {
                System.setSecurityManager(originalSm);
            } catch (Throwable ignored) {
                // if JDK forbids restoring, nothing we can do here
            }
        }
    }

    private static String runMainCaptureStdout(String[] args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            BuildAnalyzerCli.main(args);
        } finally {
            System.setOut(originalOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    // ---------------- existing success-path tests  ----------------

    @Test
    void main_shouldPrintHumanReadableSummary_inTextMode() {
        String[] args = {"sample-logs/build-parent.log"};

        String output = runMainCaptureStdout(args);

        assertTrue(output.contains("=== Build Analyzer CLI ==="));
        assertTrue(output.contains("Total build time"));
        assertTrue(output.contains("Modules total time"));
        assertTrue(output.contains("Other / overhead"));
        assertTrue(output.contains("Modules by time (share of whole build):"));
        assertTrue(output.contains("Slowest module: core"));
    }

    @Test
    void main_shouldPrintJsonSummary_whenJsonPrettyEnabled() {
        String[] args = {"-jp", "sample-logs/build-parent.log"};

        String output = runMainCaptureStdout(args).trim();

        assertTrue(output.startsWith("{"));
        assertTrue(output.contains("\"totalSeconds\""));
        assertTrue(output.contains("\"modules\""));
        assertTrue(output.contains("\"name\""));
        assertTrue(output.contains("\"seconds\""));
        // pretty printing typically contains newlines/indent
        assertTrue(output.contains("\n"));
    }

    @Test
    void main_shouldPrintJsonSummary_whenJsonEnabledButNotPretty() {
        String[] args = {"-j", "sample-logs/build-parent.log"};

        String output = runMainCaptureStdout(args).trim();

        assertTrue(output.startsWith("{"));
        assertTrue(output.contains("\"totalSeconds\""));
        assertTrue(output.contains("\"modules\""));
        // non-pretty gson should be mostly single-line (no guarantee, but usually true)
        assertFalse(output.contains("\n  \"")); // very typical pretty indent pattern
    }

    // ---------------- error-path tests ----------------

    @Test
    void main_shouldExit2_whenLogFileNotFound() {
        RunResult r = runMainExpectExit(new String[]{"this-file-should-not-exist-12345.log"});

        assertEquals(2, r.exitCode());
        assertTrue(r.err().contains("ERROR: File not found:"));
        assertTrue(r.err().contains("this-file-should-not-exist-12345.log"));
    }

    @Test
    void main_shouldExit1_whenArgsInvalid_orMissingRequiredArgs() {
        RunResult r = runMainExpectExit(new String[]{});

        assertEquals(1, r.exitCode());
        assertTrue(r.err().startsWith("Usage: build-analyzer"));
    }

    @Test
    void main_shouldExit4_whenIOExceptionOccurs_readingDirectoryAsFile(@TempDir Path tempDir) {
        // tempDir exists, but parser will try to read it as a file -> IOException (e.g., IsDirectoryException)
        RunResult r = runMainExpectExit(new String[]{tempDir.toString()});

        assertEquals(4, r.exitCode());
        assertTrue(r.err().contains("ERROR: Failed to read log file:"));
    }

    @Test
    void main_shouldExit99_onUnexpectedError_whenArgsArrayIsNull() {
        RunResult r = runMainExpectExit(null);

        assertEquals(99, r.exitCode());
        assertTrue(r.err().contains("Unexpected error:"));
    }

    // ---------------- new tests: DIRECTORY / PATTERN aggregation ----------------

    @Test
    void main_shouldAggregateDirectory_inTextMode(@TempDir Path tempDir) throws Exception {
        Path sample = Paths.get("sample-logs/build-parent.log");
        assertTrue(Files.exists(sample), "sample-logs/build-parent.log should exist for this test");

        Files.copy(sample, tempDir.resolve("build-1.log"));
        Files.copy(sample, tempDir.resolve("build-2.log"));

        String output = runMainCaptureStdout(new String[]{"--dir", tempDir.toString()});

        assertTrue(output.contains("Build Analyzer CLI (aggregate: directory)"));
        assertTrue(output.contains("Builds analyzed"));
        assertTrue(output.contains("Modules by average time:"));
        assertTrue(output.contains("Log files (2):"));
    }

    @Test
    void main_shouldExit7_whenDirHasNoLogFiles(@TempDir Path tempDir) {
        RunResult r = runMainExpectExit(new String[]{"--dir", tempDir.toString()});

        assertEquals(7, r.exitCode());
        assertTrue(r.err().contains("No .log files found in directory"));
    }

    @Test
    void main_shouldAggregatePattern_inJsonPretty() {
        String[] args = {"-jp", "--aggregate", "sample-logs/build-*.log"};

        String output = runMainCaptureStdout(args).trim();

        assertTrue(output.startsWith("{"));
        assertTrue(output.contains("\"mode\""));
        assertTrue(output.contains("\"logFiles\""));
        assertTrue(output.contains("\"summary\""));
        assertTrue(output.contains("\"buildCount\""));
    }
}
