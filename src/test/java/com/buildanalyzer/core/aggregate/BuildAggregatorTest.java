package com.buildanalyzer.core.aggregate;

import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildAggregatorTest {

    @Test
    void aggregate_shouldComputeTotalsAndModuleStatsCorrectly() {
        // Build #1: total = 10.0 s
        BuildSummary build1 = new BuildSummary(
                10.0,
                List.of(
                        new ModuleSummary("core", 6.0),
                        new ModuleSummary("webapp", 4.0)
                )
        );

        // Build #2: total = 14.0 s
        BuildSummary build2 = new BuildSummary(
                14.0,
                List.of(
                        new ModuleSummary("core", 8.0),
                        new ModuleSummary("webapp", 6.0)
                )
        );

        BuildAggregator aggregator = new BuildAggregator();
        AggregatedSummary summary = aggregator.aggregate(List.of(build1, build2));

        // --- high-level totals ---
        assertEquals(2, summary.buildCount());
        assertEquals(12.0, summary.averageTotalSeconds(), 0.0001); // (10 + 14) / 2
        assertEquals(10.0, summary.minTotalSeconds(), 0.0001);
        assertEquals(14.0, summary.maxTotalSeconds(), 0.0001);

        // --- module stats ---
        List<ModuleStats> modules = summary.modules();
        assertEquals(2, modules.size());

        // modules are sorted by averageSeconds descending -> core first, webapp second
        ModuleStats core = modules.get(0);
        ModuleStats webapp = modules.get(1);

        // core: samples = [6, 8]
        assertEquals("core", core.name());
        assertEquals(2, core.buildCount());
        assertEquals(7.0, core.averageSeconds(), 0.0001);   // (6 + 8) / 2
        assertEquals(6.0, core.minSeconds(), 0.0001);
        assertEquals(8.0, core.maxSeconds(), 0.0001);

        // webapp: samples = [4, 6]
        assertEquals("webapp", webapp.name());
        assertEquals(2, webapp.buildCount());
        assertEquals(5.0, webapp.averageSeconds(), 0.0001); // (4 + 6) / 2
        assertEquals(4.0, webapp.minSeconds(), 0.0001);
        assertEquals(6.0, webapp.maxSeconds(), 0.0001);
    }

    @Test
    void aggregate_singleBuildStillWorks() {
        BuildSummary build = new BuildSummary(
                8.0,
                List.of(
                        new ModuleSummary("service", 3.0),
                        new ModuleSummary("core", 5.0)
                )
        );

        BuildAggregator aggregator = new BuildAggregator();
        AggregatedSummary summary = aggregator.aggregate(List.of(build));

        assertEquals(1, summary.buildCount());
        assertEquals(8.0, summary.averageTotalSeconds(), 0.0001);
        assertEquals(8.0, summary.minTotalSeconds(), 0.0001);
        assertEquals(8.0, summary.maxTotalSeconds(), 0.0001);

        List<ModuleStats> modules = summary.modules();
        assertEquals(2, modules.size());
        assertEquals("core", modules.get(0).name());
        assertEquals("service", modules.get(1).name());
    }

    @Test
    void aggregate_emptyListShouldThrow() {
        BuildAggregator aggregator = new BuildAggregator();
        assertThrows(IllegalArgumentException.class,
                () -> aggregator.aggregate(List.of()));
    }
}
