package com.buildanalyzer.core.aggregate;

/**
 * Aggregated statistics for a single module across many builds.
 */
public record ModuleStats(
        String name,

        // total module time
        double averageSeconds,
        double minSeconds,
        double maxSeconds,
        int buildCount,

        // test time per module
        double averageTestSeconds,
        double minTestSeconds,
        double maxTestSeconds,
        int totalTestsRun,
        int totalFailures,
        int totalErrors,
        int totalSkipped,

        // compilation workload (average source files per build)
        double averageMainSourceFiles,
        double averageTestSourceFiles
) {}
