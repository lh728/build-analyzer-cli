package com.buildanalyzer.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CliArgumentParserTest {

    // ---------------- helpers: trap System.exit + capture stderr ----------------

    private static final class ExitTrappedError extends Error {
        final int status;
        ExitTrappedError(int status) {
            super("System.exit(" + status + ") trapped");
            this.status = status;
        }
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static final class NoExitSecurityManager extends SecurityManager {
        @Override public void checkExit(int status) { throw new ExitTrappedError(status); }
        @Override public void checkPermission(java.security.Permission perm) { /* allow */ }
        @Override public void checkPermission(java.security.Permission perm, Object ctx) { /* allow */ }
    }

    private record ExitResult(int exitCode, String err) {}

    @SuppressWarnings({"deprecation", "removal"})
    private static ExitResult parseExpectExit(String[] args) {
        PrintStream originalErr = System.err;
        SecurityManager originalSm = System.getSecurityManager();

        ByteArrayOutputStream err = new ByteArrayOutputStream();

        try {
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));

            try {
                System.setSecurityManager(new NoExitSecurityManager());
            } catch (UnsupportedOperationException e) {
                fail("""
                     Current JDK forbids installing SecurityManager, so we cannot trap System.exit.
                     If you're on JDK 18+/21+, add this JVM arg for tests:
                       -Djava.security.manager=allow
                     """);
            }

            try {
                CliArgumentParser.parse(args);
                fail("Expected System.exit(...) but parse returned normally.");
                return null; // unreachable
            } catch (ExitTrappedError ex) {
                return new ExitResult(ex.status, err.toString(StandardCharsets.UTF_8));
            }
        } finally {
            System.setErr(originalErr);
            try { System.setSecurityManager(originalSm); } catch (Throwable ignored) {}
        }
    }

    // ---------------- your existing success-path tests (kept) ----------------

    @Test
    void parse_plainTextMode_withOnlyLogFile() {
        String[] args = {"sample-logs/build-parent.log"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertFalse(opts.jsonOutput());
        assertFalse(opts.prettyJson());
        assertEquals(Mode.SINGLE_LOG, opts.mode());
        assertEquals("sample-logs/build-parent.log", opts.logFile());
    }

    @Test
    void parse_jsonMode_withShortOptionJ() {
        String[] args = {"-j", "sample-logs/build-parent.log"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertTrue(opts.jsonOutput());
        assertFalse(opts.prettyJson());
        assertEquals(Mode.SINGLE_LOG, opts.mode());
        assertEquals("sample-logs/build-parent.log", opts.logFile());
    }

    @Test
    void parse_jsonPretty_withCombinedShortOptionsLowercase() {
        String[] args = {"-jp", "sample-logs/build-parent.log"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertTrue(opts.jsonOutput());
        assertTrue(opts.prettyJson());
        assertEquals(Mode.SINGLE_LOG, opts.mode());
        assertEquals("sample-logs/build-parent.log", opts.logFile());
    }

    @Test
    void parse_jsonPretty_withCombinedShortOptionsUppercase() {
        String[] args = {"-JP", "sample-logs/build-parent.log"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertTrue(opts.jsonOutput());
        assertTrue(opts.prettyJson());
        assertEquals(Mode.SINGLE_LOG, opts.mode());
        assertEquals("sample-logs/build-parent.log", opts.logFile());
    }

    @Test
    void parse_jsonPretty_withLongOptions() {
        String[] args = {"--json", "--pretty", "sample-logs/build-parent.log"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertTrue(opts.jsonOutput());
        assertTrue(opts.prettyJson());
        assertEquals(Mode.SINGLE_LOG, opts.mode());
        assertEquals("sample-logs/build-parent.log", opts.logFile());
    }

    // ---------------- additional success tests for new modes ----------------

    @Test
    void parse_directoryMode_withLongOptionDir() {
        String[] args = {"--dir", "ci-logs/"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertEquals(Mode.DIRECTORY, opts.mode());
        assertEquals("ci-logs/", opts.directory());
        assertNull(opts.logFile());
        assertNull(opts.aggregatePattern());
    }

    @Test
    void parse_directoryMode_withShortOptionD() {
        String[] args = {"-d", "ci-logs/"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertEquals(Mode.DIRECTORY, opts.mode());
        assertEquals("ci-logs/", opts.directory());
        assertNull(opts.logFile());
        assertNull(opts.aggregatePattern());
    }

    @Test
    void parse_patternMode_withLongOptionAggregate() {
        String[] args = {"--aggregate", "ci-logs/build-*.log"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertEquals(Mode.PATTERN, opts.mode());
        assertEquals("ci-logs/build-*.log", opts.aggregatePattern());
        assertNull(opts.logFile());
        assertNull(opts.directory());
    }

    @Test
    void parse_patternMode_withShortOptionA() {
        String[] args = {"-a", "ci-logs/build-*.log"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertEquals(Mode.PATTERN, opts.mode());
        assertEquals("ci-logs/build-*.log", opts.aggregatePattern());
        assertNull(opts.logFile());
        assertNull(opts.directory());
    }

    @Test
    void parse_combinedShortOptions_pj_shouldAlsoWork() {
        String[] args = {"-pj", "sample-logs/build-parent.log"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertTrue(opts.jsonOutput());
        assertTrue(opts.prettyJson());
        assertEquals(Mode.SINGLE_LOG, opts.mode());
        assertEquals("sample-logs/build-parent.log", opts.logFile());
    }

    // ---------------- failure-path tests (System.exit(1)) ----------------

    @Test
    void parse_shouldExit1_andPrintUsage_whenArgsEmpty() {
        ExitResult r = parseExpectExit(new String[]{});

        assertEquals(1, r.exitCode());
        assertTrue(r.err().contains("Usage: build-analyzer"));
        assertTrue(r.err().contains("Options:"));
    }

    @Test
    void parse_shouldExit1_onUnknownLongOption() {
        ExitResult r = parseExpectExit(new String[]{"--wat", "x.log"});

        assertEquals(1, r.exitCode());
        assertTrue(r.err().contains("ERROR: Unknown option: --wat"));
        assertTrue(r.err().contains("Usage: build-analyzer"));
    }

    @Test
    void parse_shouldExit1_onUnknownShortOption() {
        ExitResult r = parseExpectExit(new String[]{"-x", "x.log"});

        assertEquals(1, r.exitCode());
        assertTrue(r.err().contains("ERROR: Unknown option: -x"));
        assertTrue(r.err().contains("Usage: build-analyzer"));
    }

    @Test
    void parse_shouldExit1_whenPrettyWithoutJson() {
        ExitResult r = parseExpectExit(new String[]{"-p", "sample-logs/build-parent.log"});

        assertEquals(1, r.exitCode());
        assertTrue(r.err().contains("ERROR: --pretty / -p can only be used together with --json / -j."));
        assertTrue(r.err().contains("Usage: build-analyzer"));
    }

    @Test
    void parse_shouldExit1_whenDirMissingValue_long() {
        ExitResult r = parseExpectExit(new String[]{"--dir"});

        assertEquals(1, r.exitCode());
        assertTrue(r.err().contains("ERROR: --dir requires a directory path."));
        assertTrue(r.err().contains("Usage: build-analyzer"));
    }

    @Test
    void parse_shouldExit1_whenDirMissingValue_short() {
        ExitResult r = parseExpectExit(new String[]{"-d"});

        assertEquals(1, r.exitCode());
        assertTrue(r.err().contains("ERROR: -d requires a directory path."));
        assertTrue(r.err().contains("Usage: build-analyzer"));
    }

    @Test
    void parse_shouldExit1_whenAggregateMissingValue_long() {
        ExitResult r = parseExpectExit(new String[]{"--aggregate"});

        assertEquals(1, r.exitCode());
        assertTrue(r.err().contains("ERROR: --aggregate requires a glob pattern."));
        assertTrue(r.err().contains("Usage: build-analyzer"));
    }

    @Test
    void parse_shouldExit1_whenAggregateMissingValue_short() {
        ExitResult r = parseExpectExit(new String[]{"-a"});

        assertEquals(1, r.exitCode());
        assertTrue(r.err().contains("ERROR: -a requires a glob pattern."));
        assertTrue(r.err().contains("Usage: build-analyzer"));
    }

    @Test
    void parse_shouldExit1_whenPositionalLogCombinedWithDir() {
        ExitResult r = parseExpectExit(new String[]{"--dir", "ci-logs/", "a.log"});

        assertEquals(1, r.exitCode());
        assertTrue(r.err().contains("ERROR: Positional <maven-log-file> cannot be combined with --dir/--aggregate."));
        assertTrue(r.err().contains("Usage: build-analyzer"));
    }

    @Test
    void parse_shouldExit1_whenMultipleModesSpecified_dirThenAggregate() {
        ExitResult r = parseExpectExit(new String[]{"--dir", "ci-logs/", "--aggregate", "ci-logs/*.log"});

        assertEquals(1, r.exitCode());
        assertTrue(r.err().contains("ERROR: Only one of <maven-log-file>, --dir, or --aggregate can be used."));
        assertTrue(r.err().contains("Usage: build-analyzer"));
    }

}
