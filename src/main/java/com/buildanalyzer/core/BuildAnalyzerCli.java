package com.buildanalyzer.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.function.Consumer;

/**
 * @author lhjls
 */
public class BuildAnalyzerCli {

    public static void main(String[] args) throws Exception {
        CliOptions options = CliArgumentParser.parse(args);

        Path logPath = Paths.get(options.logFile());

        if (!Files.exists(logPath)) {
            System.err.println("File not found: " + logPath.toAbsolutePath());
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

    private static void printJson(BuildSummary summary, boolean pretty) {
        Gson gson = pretty
                ? new GsonBuilder().setPrettyPrinting().create()
                : new Gson();
        System.out.println(gson.toJson(summary));
    }

    private static void printSummary(Path logPath, BuildSummary summary) {
        double totalBuild = summary.getTotalSeconds();
        double totalModules = summary.getModules().stream()
                .mapToDouble(ModuleSummary::getSeconds)
                .sum();
        // To prevent floating-point errors, the minimum value should not be less than 0.
        double overhead = Math.max(0.0, totalBuild - totalModules);

        System.out.println("=== Build Analyzer CLI ===");
        System.out.println("Log file : " + logPath);
        System.out.println();

        // clarify total time structure
        System.out.printf("Total build time   : %.3f s%n", totalBuild);
        System.out.printf("Modules total time : %.3f s (%.1f%% of build)%n",
                totalModules, totalModules / totalBuild * 100.0);
        System.out.printf("Other / overhead   : %.3f s (%.1f%% of build)%n%n",
                overhead, overhead / totalBuild * 100.0);

        System.out.println("Modules by time (share of whole build):");

        // Print the list of modules in descending order of time taken, with each module still listed as a percentage of the total build time.
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

        // slowest module
        ModuleSummary slowest = summary.getModules().stream()
                .max(Comparator.comparingDouble(ModuleSummary::getSeconds))
                .orElse(null);

        if (slowest != null) {
            double percentOfBuild = slowest.getSeconds() / totalBuild * 100.0;
            System.out.printf("Slowest module: %s (%.3f s, %.1f%% of build)%n",
                    slowest.getName(), slowest.getSeconds(), percentOfBuild);
        }
    }
}
