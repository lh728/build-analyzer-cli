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
 * CLI command: run 'mvn clean install' (or Maven Wrapper) and analyze the captured log.
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

        // 2) 准备 log 文件位置：<project>/.build-analyzer/logs/clean-install-YYYYMMDD-HHmmss.log
        Path logDir = projectDir.resolve(".build-analyzer").resolve("logs");
        Files.createDirectories(logDir);

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path logFile = logDir.resolve("clean-install-" + timestamp + ".log");

        // 3) 构造 Maven 命令（优先使用 mvnw / mvnw.cmd）
        List<String> cmd = buildMavenCommand(projectDir, options.extraMavenArgs());

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
            System.err.println("ERROR: Maven build failed with exit code " + exitCode + ".");
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

    /**
     * 构造 Maven 命令:
     * 1) 如果项目根有 Maven Wrapper，优先用 mvnw/mvnw.cmd
     * 2) 否则 Windows 用 mvn.cmd，其他系统用 mvn
     */
    private List<String> buildMavenCommand(Path projectDir, List<String> extraArgs) throws Exception {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        // 先尝试 Maven Wrapper
        Path mvnw = projectDir.resolve(isWindows ? "mvnw.cmd" : "mvnw");
        List<String> cmd = new ArrayList<>();

        if (Files.exists(mvnw) && Files.isRegularFile(mvnw)) {
            // 直接用 wrapper（绝对路径，避免 PATH 问题）
            cmd.add(mvnw.toAbsolutePath().toString());
        } else {
            // wrapper 不存在，回退到系统 Maven
            cmd.add(isWindows ? "mvn.cmd" : "mvn");
        }

        cmd.add("clean");
        cmd.add("install");
        cmd.addAll(extraArgs);

        return cmd;
    }
}
