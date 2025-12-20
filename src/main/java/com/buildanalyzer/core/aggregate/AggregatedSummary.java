package com.buildanalyzer.core.aggregate;

import java.util.List;

/**
 * @author lhjls
 * Aggregated statistics across multiple builds
 */
public record AggregatedSummary(
        int buildCount,
        double averageTotalSeconds,
        double minTotalSeconds,
        double maxTotalSeconds,
        List<ModuleStats> modules
) {}
