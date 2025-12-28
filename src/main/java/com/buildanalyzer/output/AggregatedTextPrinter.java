package com.buildanalyzer.output;

import com.buildanalyzer.core.aggregate.AggregatedSummary;
import com.buildanalyzer.core.aggregate.ModuleStats;

import java.nio.file.Path;
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
}

