package com.buildanalyzer.core.parser;

import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class MavenLogParserTest {

    // -------- existing integration test (keep) --------

    @Test
    void shouldParseTotalTimeAndModulesFromSampleParentLog() throws Exception {
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

    // -------- parseTotalTime unit tests --------

    @Test
    void parseTotalTime_shouldSupportSecondsVariants() {
        MavenLogParser parser = new MavenLogParser();

        assertEquals(8.294, parser.parseTotalTime(List.of("[INFO] Total time:  8.294 s")), 0.0001);
        assertEquals(2.0, parser.parseTotalTime(List.of("[INFO] Total time:  2 sec")), 0.0001);
        assertEquals(3.0, parser.parseTotalTime(List.of("[INFO] Total time:  3 seconds")), 0.0001);
        assertEquals(4.0, parser.parseTotalTime(List.of("[INFO] Total time:  4 Second")), 0.0001);
    }

    @Test
    void parseTotalTime_shouldConvertMillisAndMinutes() {
        MavenLogParser parser = new MavenLogParser();

        assertEquals(1.234, parser.parseTotalTime(List.of("[INFO] Total time:  1234 ms")), 0.0001);
        assertEquals(120.0, parser.parseTotalTime(List.of("[INFO] Total time:  2 min")), 0.0001);
        assertEquals(180.0, parser.parseTotalTime(List.of("[INFO] Total time:  3 minutes")), 0.0001);
    }

    @Test
    void parseTotalTime_shouldUseLastTotalTimeLine() {
        MavenLogParser parser = new MavenLogParser();

        List<String> lines = List.of(
                "[INFO] something ...",
                "[INFO] Total time:  1.000 s",
                "[INFO] more ...",
                "[INFO] Total time:  2.500 s"   // should win
        );

        assertEquals(2.5, parser.parseTotalTime(lines), 0.0001);
    }

    @Test
    void parseTotalTime_shouldReturnValueForUnknownUnit_withoutConversion() {
        MavenLogParser parser = new MavenLogParser();

        // unknown unit -> default branch returns value as-is
        assertEquals(7.0, parser.parseTotalTime(List.of("[INFO] Total time:  7 bananas")), 0.0001);
    }

    @Test
    void parseTotalTime_shouldThrow_whenMissing() {
        MavenLogParser parser = new MavenLogParser();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parser.parseTotalTime(List.of("[INFO] no total time here")));

        assertTrue(ex.getMessage().contains("Could not find 'Total time'"));
    }

    // -------- parseReactorSummary unit tests --------

    @Test
    void parseReactorSummary_shouldParseModules_andStopOnBuildSuccess() {
        MavenLogParser parser = new MavenLogParser();

        List<String> lines = List.of(
                "[INFO] some header",
                "[INFO] Reactor Summary for parent-project 1.0-SNAPSHOT:",
                "[INFO] parent-project .............................. SUCCESS [  0.247 s]",
                "[INFO] core ........................................ SUCCESS [  4.637 s]",
                "[INFO] BUILD SUCCESS",
                // should NOT be parsed because we stop at BUILD SUCCESS
                "[INFO] service ..................................... SUCCESS [  1.648 s]"
        );

        List<ModuleSummary> modules = parser.parseReactorSummary(lines);
        assertEquals(2, modules.size());

        Map<String, Double> map = modules.stream()
                .collect(Collectors.toMap(ModuleSummary::getName, ModuleSummary::getSeconds));

        assertEquals(0.247, map.get("parent-project"), 0.001);
        assertEquals(4.637, map.get("core"), 0.001);
        assertFalse(map.containsKey("service"));
    }

    @Test
    void parseReactorSummary_shouldStopOnBuildFailure() {
        MavenLogParser parser = new MavenLogParser();

        List<String> lines = List.of(
                "[INFO] Reactor Summary:",
                "[INFO] core ........................................ FAILURE [  4.637 s]",
                "[INFO] BUILD FAILURE",
                "[INFO] webapp ...................................... SUCCESS [  1.548 s]"
        );

        List<ModuleSummary> modules = parser.parseReactorSummary(lines);
        assertEquals(1, modules.size());
        assertEquals("core", modules.get(0).getName());
    }

    @Test
    void parseReactorSummary_shouldIgnoreNonMatchingLines_insideSummary() {
        MavenLogParser parser = new MavenLogParser();

        List<String> lines = List.of(
                "[INFO] Reactor Summary:",
                "[INFO] -------------------------------------------------------",
                "[INFO] this line does not match module pattern",
                "[INFO] webapp ..................................... SUCCESS [  1.548 s]",
                "[INFO] BUILD SUCCESS"
        );

        List<ModuleSummary> modules = parser.parseReactorSummary(lines);
        assertEquals(1, modules.size());
        assertEquals("webapp", modules.get(0).getName());
        assertEquals(1.548, modules.get(0).getSeconds(), 0.001);
    }

    @Test
    void parseReactorSummary_shouldThrow_whenNoModulesFound() {
        MavenLogParser parser = new MavenLogParser();

        List<String> lines = List.of(
                "[INFO] Reactor Summary:",
                "[INFO] -----------",
                "[INFO] (no module lines here)",
                "[INFO] BUILD SUCCESS"
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parser.parseReactorSummary(lines));

        assertTrue(ex.getMessage().contains("Could not find any modules in 'Reactor Summary'"));
    }

    // -------- parse(Path) integration tests without sample logs --------

    @Test
    void parse_shouldReadFile_andReturnSummary(@TempDir Path tempDir) throws Exception {
        Path log = tempDir.resolve("build.log");

        List<String> content = List.of(
                "[INFO] Reactor Summary:",
                "[INFO] parent-project .............................. SUCCESS [  0.100 s]",
                "[INFO] core ........................................ SUCCESS [  0.200 s]",
                "[INFO] BUILD SUCCESS",
                "[INFO] Total time:  300 ms" // 0.300s
        );
        Files.write(log, content, StandardCharsets.UTF_8);

        MavenLogParser parser = new MavenLogParser();
        BuildSummary summary = parser.parse(log);

        assertEquals(0.300, summary.getTotalSeconds(), 0.0001);
        assertEquals(2, summary.getModules().size());
    }

    @Test
    void parse_shouldThrow_whenTotalTimeMissing_evenIfModulesExist(@TempDir Path tempDir) throws Exception {
        Path log = tempDir.resolve("build.log");

        List<String> content = List.of(
                "[INFO] Reactor Summary:",
                "[INFO] core ........................................ SUCCESS [  0.200 s]",
                "[INFO] BUILD SUCCESS"
                // no Total time line
        );
        Files.write(log, content, StandardCharsets.UTF_8);

        MavenLogParser parser = new MavenLogParser();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> parser.parse(log));

        assertTrue(ex.getMessage().contains("Could not find 'Total time'"));
    }
}
