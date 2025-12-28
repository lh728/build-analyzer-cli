package com.buildanalyzer.core.aggregate;

import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates many single-build summaries into cross-build statistics.
 */
public class BuildAggregator {

    public AggregatedSummary aggregate(List<BuildSummary> builds) {
        if (builds == null || builds.isEmpty()) {
            throw new IllegalArgumentException("builds must not be null or empty");
        }

        int buildCount = builds.size();

        double sumTotal = 0.0;
        double minTotal = Double.POSITIVE_INFINITY;
        double maxTotal = 0.0;

        Map<String, ModuleAccumulator> modules = new HashMap<>();

        for (BuildSummary build : builds) {
            double total = build.getTotalSeconds();
            sumTotal += total;
            if (total < minTotal) minTotal = total;
            if (total > maxTotal) maxTotal = total;

            for (ModuleSummary m : build.getModules()) {
                ModuleAccumulator acc =
                        modules.computeIfAbsent(m.getName(), ModuleAccumulator::new);
                acc.add(m);
            }
        }

        double avgTotal = sumTotal / buildCount;

        List<ModuleStats> moduleStats = modules.values().stream()
                .map(ModuleAccumulator::toStats)
                .sorted(Comparator.comparingDouble(ModuleStats::averageSeconds).reversed())
                .toList();

        return new AggregatedSummary(buildCount, avgTotal, minTotal, maxTotal, moduleStats);
    }

    // -------- internal per-module accumulator --------

    private static final class ModuleAccumulator {
        final String name;
        int buildCount;

        // total module time
        double totalSecondsSum;
        double minSeconds = Double.POSITIVE_INFINITY;
        double maxSeconds = 0.0;

        // test-related
        boolean hasAnyTests;
        double testSecondsSum;
        double minTestSeconds = Double.POSITIVE_INFINITY;
        double maxTestSeconds = 0.0;
        int totalTestsRun;
        int totalFailures;
        int totalErrors;
        int totalSkipped;

        // compilation workload
        int mainSourceFilesSum;
        int testSourceFilesSum;

        ModuleAccumulator(String name) {
            this.name = name;
        }

        void add(ModuleSummary m) {
            buildCount++;

            // --- total module time ---
            double secs = m.getSeconds();
            totalSecondsSum += secs;
            if (secs < minSeconds) minSeconds = secs;
            if (secs > maxSeconds) maxSeconds = secs;

            // --- tests ---
            int run = m.getTestsRun();
            int failures = m.getFailures();
            int errors = m.getErrors();
            int skipped = m.getSkipped();
            double testSecs = m.getTestTimeSeconds();

            boolean thisBuildHasTests =
                    run > 0 || failures > 0 || errors > 0 || skipped > 0 || testSecs > 0.0;

            if (thisBuildHasTests) {
                hasAnyTests = true;
                testSecondsSum += testSecs;
                if (testSecs < minTestSeconds) minTestSeconds = testSecs;
                if (testSecs > maxTestSeconds) maxTestSeconds = testSecs;

                totalTestsRun += run;
                totalFailures += failures;
                totalErrors += errors;
                totalSkipped += skipped;
            } else {
                // 没测试也参与平均（testSecs 通常是 0）
                testSecondsSum += testSecs;
            }

            // --- compilation workload ---
            mainSourceFilesSum += m.getMainSourceFiles();
            testSourceFilesSum += m.getTestSourceFiles();
        }

        ModuleStats toStats() {
            double avgSeconds = totalSecondsSum / buildCount;
            double avgTestSeconds = testSecondsSum / buildCount;

            double minTest = hasAnyTests ? minTestSeconds : 0.0;
            double maxTest = hasAnyTests ? maxTestSeconds : 0.0;

            double avgMainSources = (double) mainSourceFilesSum / buildCount;
            double avgTestSources = (double) testSourceFilesSum / buildCount;

            return new ModuleStats(
                    name,
                    avgSeconds,
                    minSeconds,
                    maxSeconds,
                    buildCount,
                    avgTestSeconds,
                    minTest,
                    maxTest,
                    totalTestsRun,
                    totalFailures,
                    totalErrors,
                    totalSkipped,
                    avgMainSources,
                    avgTestSources
            );
        }
    }
}
