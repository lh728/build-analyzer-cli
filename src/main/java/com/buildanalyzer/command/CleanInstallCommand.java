package com.buildanalyzer.command;

import com.buildanalyzer.cli.CliOptions;
import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.parser.MavenLogParser;
import com.buildanalyzer.output.JsonOutputWriter;
import com.buildanalyzer.output.SingleBuildTextPrinter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI command: run 'mvn clean install' (or Maven Wrapper) and analyze the captured log.
 *
 * NOTE:
 *   For reliability, --clean-install does NOT allow parallel build (-T/--threads),
 *   because interleaved logs break per-module attribution.
 */
public class CleanInstallCommand implements CliCommand {

    private final MavenLogParser parser = new MavenLogParser();
    private final SingleBuildTextPrinter textPrinter = new SingleBuildTextPrinter();
    private final JsonOutputWriter jsonWriter = new JsonOutputWriter();

    @Override
    public void execute(CliOptions options) throws Exception {
        // 0) Reject -T/--threads in clean-install mode (user-provided extra args)
        ensureNoParallelArgs(options.extraMavenArgs());

        // 1) project dir (default ".")
        Path projectDir = options.projectDir() != null
                ? Paths.get(options.projectDir())
                : Paths.get(".");

        if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
            System.err.println("ERROR: Project directory not found: " + projectDir.toAbsolutePath());
            System.exit(2);
        }

        Path pom = projectDir.resolve("pom.xml");
        if (!Files.exists(pom)) {
            System.err.println("ERROR: No pom.xml found in project directory: " + projectDir.toAbsolutePath());
            System.exit(2);
        }

        // 2) log file location: <project>/.build-analyzer/logs/clean-install-YYYYMMDD-HHmmss.log
        Path logDir = projectDir.resolve(".build-analyzer").resolve("logs");
        Files.createDirectories(logDir);

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path logFile = logDir.resolve("clean-install-" + timestamp + ".log");

        // 3) build Maven command (prefer mvnw)
        List<String> cmd = buildMavenCommand(projectDir, options.extraMavenArgs());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(projectDir.toFile());
        pb.redirectErrorStream(true); // merge stderr into stdout

        System.out.println("Running: " + String.join(" ", cmd));
        System.out.println("Working directory: " + projectDir.toAbsolutePath());
        System.out.println("Log will be captured at: " + logFile.toAbsolutePath());
        System.out.println();

        Process process = pb.start();

        // 4) tee: console + logFile
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedWriter writer = Files.newBufferedWriter(logFile)) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                writer.write(line);
                writer.newLine();
            }
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            System.err.println();
            System.err.println("ERROR: Maven build failed with exit code " + exitCode + ".");
            System.err.println("       Log captured at: " + logFile.toAbsolutePath());
            System.exit(exitCode);
        }

        // 4.5) Safety: reject parallel build detected from captured log
        if (logIndicatesParallelBuild(logFile)) {
            System.err.println();
            System.err.println("ERROR: Parallel build detected in captured log (MultiThreadedBuilder / -T).");
            System.err.println("       --clean-install requires a single-thread build for reliable analysis.");
            System.err.println("       Fix: remove -T/--threads from your Maven args AND check .mvn/maven.config.");
            System.err.println("       Log: " + logFile.toAbsolutePath());
            System.exit(2);
        }

        // 5) parse + output
        System.out.println();
        System.out.println("=== Analyzing captured build log ===");
        System.out.println("Log file : " + logFile.toAbsolutePath());
        System.out.println();

        BuildSummary summary = parser.parse(logFile);

        if (options.jsonOutput()) {
            jsonWriter.printSingleBuild(summary, options.prettyJson());
        } else {
            textPrinter.print(logFile, summary);
        }
    }

    /**
     * Build Maven command:
     * 1) Prefer Maven Wrapper (mvnw/mvnw.cmd)
     * 2) Else use mvn/mvn.cmd
     */
    private List<String> buildMavenCommand(Path projectDir, List<String> extraArgs) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        Path mvnw = projectDir.resolve(isWindows ? "mvnw.cmd" : "mvnw");
        List<String> cmd = new ArrayList<>();

        if (Files.exists(mvnw) && Files.isRegularFile(mvnw)) {
            cmd.add(mvnw.toAbsolutePath().toString());
        } else {
            cmd.add(isWindows ? "mvn.cmd" : "mvn");
        }

        cmd.add("clean");
        cmd.add("install");
        cmd.addAll(extraArgs);

        return cmd;
    }

    private static void ensureNoParallelArgs(List<String> extraArgs) {
        if (extraArgs == null || extraArgs.isEmpty()) return;

        for (int i = 0; i < extraArgs.size(); i++) {
            String a = extraArgs.get(i);
            if (a == null) continue;

            // -T1C  / -T4 / -T etc
            if (a.equals("-T") || a.startsWith("-T")) {
                System.err.println("ERROR: --clean-install does not support parallel Maven builds (-T).");
                System.err.println("       Remove '-T...' and run again. If you need parallel builds, run Maven");
                System.err.println("       yourself and analyze the produced log file with single-log mode.");
                System.exit(2);
            }

            // --threads or --threads=1C
            if (a.equals("--threads") || a.startsWith("--threads=")) {
                System.err.println("ERROR: --clean-install does not support parallel Maven builds (--threads).");
                System.err.println("       Remove '--threads...' and run again.");
                System.exit(2);
            }
        }
    }

    private static boolean logIndicatesParallelBuild(Path logFile) {
        try (var lines = Files.lines(logFile)) {
            return lines.anyMatch(l -> l != null && l.contains("MultiThreadedBuilder"));
        } catch (IOException e) {
            // best-effort: if we can't read the log, don't block
            return false;
        }
    }
}
