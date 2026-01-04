package com.buildanalyzer.command;

import com.buildanalyzer.cli.CliOptions;
import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.parser.MavenLogParser;
import com.buildanalyzer.output.JsonOutputWriter;
import com.buildanalyzer.output.SingleBuildTextPrinter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI command: run 'mvn clean install' and analyze the captured log.
 */
public class CleanInstallCommand implements CliCommand {

    private final MavenLogParser parser = new MavenLogParser();
    private final SingleBuildTextPrinter textPrinter = new SingleBuildTextPrinter();
    private final JsonOutputWriter jsonWriter = new JsonOutputWriter();

    @Override
    public void execute(CliOptions options) throws Exception {
        // 1) 确定工程目录（默认当前）
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

        // 2) 准备 log 文件位置：target/build-analyzer/clean-install-YYYYMMDD-HHmmss.log
        Path logDir = projectDir.resolve("target").resolve("build-analyzer");
        Files.createDirectories(logDir);

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path logFile = logDir.resolve("clean-install-" + timestamp + ".log");

        // 3) 构造 mvn 命令
        List<String> cmd = new ArrayList<>();
        cmd.add("mvn");
        cmd.add("clean");
        cmd.add("install");
        cmd.addAll(options.extraMavenArgs());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(projectDir.toFile());
        pb.redirectErrorStream(true); // stderr 合并到 stdout

        System.out.println("Running: " + String.join(" ", cmd));
        System.out.println("Working directory: " + projectDir.toAbsolutePath());
        System.out.println("Log will be captured at: " + logFile.toAbsolutePath());
        System.out.println();

        Process process = pb.start();

        // 4) tee：一边打印到控制台，一边写入 logFile
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
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
            System.err.println("ERROR: 'mvn clean install' failed with exit code " + exitCode + ".");
            System.err.println("       Log captured at: " + logFile.toAbsolutePath());
            System.exit(exitCode);
        }

        // 5) 构建成功后，用现有 parser + printer 分析刚刚的 log
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
}

