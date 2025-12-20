package com.buildanalyzer.core.aggregate;

/**
 * @author lhjls
 * Aggregated statistics for a single module across many builds.
 */
public record ModuleStats(
        String name,
        int buildCount,
        double averageSeconds,
        double minSeconds,
        double maxSeconds
) {}
