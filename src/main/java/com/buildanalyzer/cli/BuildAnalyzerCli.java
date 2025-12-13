package com.buildanalyzer.cli;

import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;
import com.buildanalyzer.core.parser.MavenLogParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
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
     * TODO: Directory aggregation mode.
     * For now, just a placeholder.
     */
    private static void runDirectoryAggregation(CliOptions options) {
        System.err.println("ERROR: Directory aggregation mode (--dir/-d) is not implemented yet.");
        System.exit(5);
    }

    /**
     * TODO: Glob pattern aggregation mode.
     * For now, just a placeholder.
     */
    private static void runPatternAggregation(CliOptions options) {
        System.err.println("ERROR: Pattern aggregation mode (--aggregate/-a) is not implemented yet.");
        System.exit(6);
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
