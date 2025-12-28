package com.buildanalyzer.output;

import com.buildanalyzer.core.aggregate.AggregatedSummary;
import com.buildanalyzer.core.aggregate.ModuleStats;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Renders aggregated build statistics as human-readable text.
 */
public class AggregatedTextPrinter {

    public void print(String modeLabel,
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

        // 1) average total workload
        System.out.println("Modules by average total time:");
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

        // 2) average test workload
        System.out.println();
        System.out.println("Modules by average test time (seconds):");

        List<ModuleStats> withTests = summary.modules().stream()
                .filter(m -> m.averageTestSeconds() > 0.0)
                .sorted(Comparator.comparingDouble(ModuleStats::averageTestSeconds).reversed())
                .toList();

        if (withTests.isEmpty()) {
            System.out.println("  (no tests detected in any module)");
        } else {
            index = 0;
            for (ModuleStats m : withTests) {
                index++;
                System.out.printf(
                        "  %d) %-15s avg %6.3f s  (min %6.3f s, max %6.3f s, builds %d, total tests %d, failures %d)%n",
                        index,
                        m.name(),
                        m.averageTestSeconds(),
                        m.minTestSeconds(),
                        m.maxTestSeconds(),
                        m.buildCount(),
                        m.totalTestsRun(),
                        m.totalFailures()
                );
            }
        }

        // 3) average compilation workload
        System.out.println();
        System.out.println("Average compilation workload per build (source files):");
        for (ModuleStats m : summary.modules()) {
            System.out.printf(
                    "  %s: main ~%.1f, test ~%.1f%n",
                    m.name(),
                    m.averageMainSourceFiles(),
                    m.averageTestSourceFiles()
            );
        }
    }
}
