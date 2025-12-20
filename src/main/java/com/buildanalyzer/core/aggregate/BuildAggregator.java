package com.buildanalyzer.core.aggregate;

import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates multiple BuildSummary instances into high-level statistics.
 * @author lhjls
 */
public final class BuildAggregator {

    public AggregatedSummary aggregate(List<BuildSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            throw new IllegalArgumentException("No builds to aggregate.");
        }

        int buildCount = summaries.size();
        double totalSum = 0.0;
        double minTotal = Double.POSITIVE_INFINITY;
        double maxTotal = Double.NEGATIVE_INFINITY;

        Map<String, ModuleAccumulator> moduleMap = new HashMap<>();

        for (BuildSummary summary : summaries) {
            double total = summary.getTotalSeconds();
            totalSum += total;
            minTotal = Math.min(minTotal, total);
            maxTotal = Math.max(maxTotal, total);

            for (ModuleSummary module : summary.getModules()) {
                moduleMap
                        .computeIfAbsent(module.getName(), ModuleAccumulator::new)
                        .addSample(module.getSeconds());
            }
        }

        double avgTotal = totalSum / buildCount;

        List<ModuleStats> modules = moduleMap.values().stream()
                .map(ModuleAccumulator::toStats)
                .sorted(Comparator.comparingDouble(ModuleStats::averageSeconds).reversed())
                .toList();

        return new AggregatedSummary(buildCount, avgTotal, minTotal, maxTotal, modules);
    }

    // ----- internal accumulator for a module -----

    private static final class ModuleAccumulator {
        private final String name;
        private int count = 0;
        private double sum = 0.0;
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;

        ModuleAccumulator(String name) {
            this.name = name;
        }

        void addSample(double seconds) {
            count++;
            sum += seconds;
            min = Math.min(min, seconds);
            max = Math.max(max, seconds);
        }

        ModuleStats toStats() {
            double avg = sum / count;
            return new ModuleStats(name, count, avg, min, max);
        }
    }
}
