package com.buildanalyzer.core;

import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;
import com.buildanalyzer.core.parser.MavenLogParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MavenLogParserTest {

    @Test
    public void shouldParseTotalTimeAndModulesFromSampleParentLog() throws Exception {
        Path logPath = Paths.get("sample-logs", "build-parent.log");

        MavenLogParser parser = new MavenLogParser();
        BuildSummary summary = parser.parse(logPath);

        assertEquals(8.294, summary.getTotalSeconds(), 0.001);
        assertEquals(4, summary.getModules().size());
        Map<String, Double> moduleTimes = summary.getModules().stream()
                .collect(Collectors.toMap(ModuleSummary::getName, ModuleSummary::getSeconds));

        assertEquals(0.247, moduleTimes.get("parent-project"), 0.001);
        assertEquals(4.637, moduleTimes.get("core"), 0.001);
        assertEquals(1.648, moduleTimes.get("service"), 0.001);
        assertEquals(1.548, moduleTimes.get("webapp"), 0.001);

        String slowestModule = summary.getModules().stream()
                .max(Comparator.comparingDouble(ModuleSummary::getSeconds))
                .map(ModuleSummary::getName)
                .orElseThrow();

        assertEquals("core", slowestModule);
    }
}
