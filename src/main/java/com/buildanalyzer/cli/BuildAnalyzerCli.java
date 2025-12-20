package com.buildanalyzer.cli;

import com.buildanalyzer.core.aggregate.AggregatedSummary;
import com.buildanalyzer.core.aggregate.BuildAggregator;
import com.buildanalyzer.core.aggregate.ModuleStats;
import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;
import com.buildanalyzer.core.parser.MavenLogParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author lhjls
 */
public class BuildAnalyzerCli {

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalStateException e) {
            // business exception（ex. Total time / Reactor Summary）
            System.err.println("ERROR: " + e.getMessage());
            System.exit(3);
        } catch (IOException e) {
            // IO
            System.err.println("ERROR: Failed to read log file: " + e.getMessage());
            System.exit(4);
        } catch (Exception e) {
            // unexpected exception
            System.err.println("Unexpected error: " + e.getClass().getSimpleName()
                    + ": " + (e.getMessage() == null ? "" : e.getMessage()));
            e.printStackTrace(System.err);
            System.exit(99);
        }
    }

    private static void run(String[] args) throws Exception {
        CliOptions options = CliArgumentParser.parse(args);

        switch (options.mode()) {
            case SINGLE_LOG -> runSingleLog(options);
            case DIRECTORY -> runDirectoryAggregation(options);
            case PATTERN -> runPatternAggregation(options);
        }
    }

    // ---------- Mode handlers ----------

    private static void runSingleLog(CliOptions options) throws Exception {
        Path logPath = Paths.get(options.logFile());

        if (!Files.exists(logPath)) {
            System.err.println("ERROR: File not found: " + logPath.toAbsolutePath());
            System.exit(2);
        }

        MavenLogParser parser = new MavenLogParser();
        BuildSummary summary = parser.parse(logPath);

        if (options.jsonOutput()) {
            printJson(summary, options.prettyJson());
        } else {
            printSummary(logPath, summary);
        }
    }

    /**
     * Analyze all *.log files directly under a directory.
     */
    private static void runDirectoryAggregation(CliOptions options) throws Exception {
        Path dir = Paths.get(options.directory());

        if (!Files.exists(dir)) {
            System.err.println("ERROR: Directory not found: " + dir.toAbsolutePath());
            System.exit(2);
        }
        if (!Files.isDirectory(dir)) {
            System.err.println("ERROR: Not a directory: " + dir.toAbsolutePath());
            System.exit(2);
        }

        List<Path> logFiles = listLogFilesInDirectory(dir);
        if (logFiles.isEmpty()) {
            System.err.println("ERROR: No .log files found in directory: " + dir.toAbsolutePath());
            System.exit(7);
        }

        aggregateAndPrint("DIRECTORY", logFiles, options);
    }

    /**
     * Analyze all log files matching a glob pattern, e.g. ci-logs/build-*.log.
     */
    private static void runPatternAggregation(CliOptions options) throws Exception {
        String raw = options.aggregatePattern();
        int lastSlash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));

        Path dir;
        String filePattern;

        if (lastSlash >= 0) {
            String dirPart = raw.substring(0, lastSlash);
            String patternPart = raw.substring(lastSlash + 1);

            dir = dirPart.isEmpty() ? Paths.get(".") : Paths.get(dirPart);
            filePattern = patternPart;
        } else {
            dir = Paths.get(".");
            filePattern = raw;
        }

        if (!Files.exists(dir)) {
            System.err.println("ERROR: Directory for pattern not found: " + dir.toAbsolutePath());
            System.exit(2);
        }
        if (!Files.isDirectory(dir)) {
            System.err.println("ERROR: Not a directory for pattern: " + dir.toAbsolutePath());
            System.exit(2);
        }

        List<Path> logFiles = listLogFilesByPattern(dir, filePattern);
        if (logFiles.isEmpty()) {
            System.err.println("ERROR: No files matching pattern '" + filePattern +
                    "' in directory: " + dir.toAbsolutePath());
            System.exit(7);
        }

        aggregateAndPrint("PATTERN", logFiles, options);
    }


    // ---------- File helpers ----------

    private static List<Path> listLogFilesInDirectory(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".log"))
                    .sorted()
                    .toList();
        }
    }

    private static List<Path> listLogFilesByPattern(Path dir, String filePattern) throws IOException {
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filePattern)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    result.add(p);
                }
            }
        }
        result.sort(null); // natural order
        return result;
    }

    // ---------- Aggregate + print ----------

    private static void aggregateAndPrint(String modeLabel,
                                          List<Path> logFiles,
                                          CliOptions options) {
        MavenLogParser parser = new MavenLogParser();
        List<BuildSummary> summaries = new ArrayList<>();

        for (Path log : logFiles) {
            try {
                summaries.add(parser.parse(log));
            } catch (IllegalStateException | IOException e) {
                System.err.println("WARN: Skipping log '" + log + "': " + e.getMessage());
            }
        }

        if (summaries.isEmpty()) {
            System.err.println("ERROR: No valid Maven builds found in the selected logs.");
            System.exit(8);
        }

        BuildAggregator aggregator = new BuildAggregator();
        AggregatedSummary aggregated = aggregator.aggregate(summaries);

        if (options.jsonOutput()) {
            printAggregatedJson(modeLabel, logFiles, aggregated, options.prettyJson());
        } else {
            printAggregatedText(modeLabel, logFiles, aggregated);
        }
    }

    private record AggregatedJsonResult(
            String mode,
            List<String> logFiles,
            AggregatedSummary summary
    ) {}


    private static void printAggregatedJson(String modeLabel,
                                            List<Path> logFiles,
                                            AggregatedSummary summary,
                                            boolean pretty) {
        List<String> fileStrings = logFiles.stream()
                .map(Path::toString)
                .toList();

        AggregatedJsonResult result =
                new AggregatedJsonResult(modeLabel, fileStrings, summary);

        Gson gson = pretty
                ? new GsonBuilder().setPrettyPrinting().create()
                : new Gson();

        System.out.println(gson.toJson(result));
    }

    private static void printAggregatedText(String modeLabel,
                                            List<Path> logFiles,
                                            AggregatedSummary summary) {
        System.out.println("=== Build Analyzer CLI (aggregate: " + modeLabel.toLowerCase() + ") ===");
        System.out.println("Log files (" + logFiles.size() + "):");
        for (Path p : logFiles) {
            System.out.println("  - " + p);
        }
        System.out.println();

        System.out.printf("Builds analyzed      : %d%n", summary.buildCount());
        System.out.printf("Total time (seconds) : avg %.3f, min %.3f, max %.3f%n",
                summary.averageTotalSeconds(),
                summary.minTotalSeconds(),
                summary.maxTotalSeconds());
        System.out.println();

        System.out.println("Modules by average time:");
        int index = 0;
        for (ModuleStats m : summary.modules()) {
            index++;
            System.out.printf(
                    "  %d) %-15s avg %6.3f s  (min %6.3f s, max %6.3f s, builds %d)%n",
                    index,
                    m.name(),
                    m.averageSeconds(),
                    m.minSeconds(),
                    m.maxSeconds(),
                    m.buildCount()
            );
        }
    }

    // ---------- JSON output ----------

    private static void printJson(BuildSummary summary, boolean pretty) {
        Gson gson = pretty
                ? new GsonBuilder().setPrettyPrinting().create()
                : new Gson();
        System.out.println(gson.toJson(summary));
    }

    // ---------- Text output ----------

    private static void printSummary(Path logPath, BuildSummary summary) {
        double totalBuild = summary.getTotalSeconds();
        double totalModules = summary.getModules().stream()
                .mapToDouble(ModuleSummary::getSeconds)
                .sum();
        double overhead = Math.max(0.0, totalBuild - totalModules);

        System.out.println("=== Build Analyzer CLI ===");
        System.out.println("Log file : " + logPath);
        System.out.println();

        System.out.printf("Total build time   : %.3f s%n", totalBuild);
        System.out.printf("Modules total time : %.3f s (%.1f%% of build)%n",
                totalModules, totalModules / totalBuild * 100.0);
        System.out.printf("Other / overhead   : %.3f s (%.1f%% of build)%n%n",
                overhead, overhead / totalBuild * 100.0);

        System.out.println("Modules by time (share of whole build):");

        summary.getModules().stream()
                .sorted(Comparator.comparingDouble(ModuleSummary::getSeconds).reversed())
                .forEachOrdered(new Consumer<>() {
                    int index = 0;

                    @Override
                    public void accept(ModuleSummary m) {
                        index++;
                        double percentOfBuild = (m.getSeconds() / totalBuild) * 100.0;
                        System.out.printf(
                                "  %d) %-15s %6.3f s  (%4.1f%% of build)%n",
                                index,
                                m.getName(),
                                m.getSeconds(),
                                percentOfBuild
                        );
                    }
                });

        System.out.println();

        summary.getModules().stream()
                .max(Comparator.comparingDouble(ModuleSummary::getSeconds))
                .ifPresent(slowest -> {
                    double percentOfBuild = slowest.getSeconds() / totalBuild * 100.0;
                    System.out.printf("Slowest module: %s (%.3f s, %.1f%% of build)%n",
                            slowest.getName(), slowest.getSeconds(), percentOfBuild);
                });
    }
}
