package com.buildanalyzer.core.parser;

import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Maven build logs into structured summaries.
 */
public class MavenLogParser {

    // [INFO] Total time:  8.294 s
    private static final Pattern TOTAL_TIME_PATTERN =
            Pattern.compile("Total time:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([a-zA-Z]+)");

    // [INFO] core ............................................... SUCCESS [  4.637 s]
    private static final Pattern MODULE_LINE_PATTERN =
            Pattern.compile("\\[INFO]\\s+(.+?)\\s+.*\\[\\s*([0-9]+(?:\\.[0-9]+)?)\\s*s]");

    // [INFO] Building core 1.0-SNAPSHOT                                         [2/4]
    private static final Pattern BUILDING_MODULE_PATTERN =
            Pattern.compile("\\[INFO] Building\\s+([^\\s]+)\\s+.*\\[[0-9]+/[0-9]+]");

    // [INFO] Compiling 1 source file with javac [...] to target\classes
    private static final Pattern COMPILE_PATTERN =
            Pattern.compile("\\[INFO] Compiling\\s+(\\d+)\\s+source file(?:s)?\\s+.*to\\s+(.+)$");

    // [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.064 s -- in ...
    private static final Pattern TEST_RESULT_WITH_TIME_PATTERN =
            Pattern.compile("\\[INFO] Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+),\\s*Time elapsed:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*s");

    // [INFO] --- clean:3.4.0:clean (default-clean) @ core ---
    private static final Pattern PLUGIN_HEADER_PATTERN =
            Pattern.compile("\\[INFO] ---\\s+(.+?)\\s+\\(.*");

    public BuildSummary parse(Path logPath) throws IOException {
        List<String> lines = Files.readAllLines(logPath);

        double totalSeconds = parseTotalTime(lines);

        // Pass 1: per-module metrics (compile / test / pipeline)
        Map<String, ModuleMetrics> metricsByModule = parseModuleMetrics(lines);

        // Pass 2: Reactor Summary -> fill module total time + preserve order
        List<ModuleSummary> modules = parseReactorSummary(lines, metricsByModule);

        return new BuildSummary(totalSeconds, modules);
    }

    // ---------- total time ----------

    double parseTotalTime(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line.contains("Total time:")) {
                Matcher m = TOTAL_TIME_PATTERN.matcher(line);
                if (m.find()) {
                    double value = Double.parseDouble(m.group(1));
                    String unit = m.group(2).toLowerCase();
                    return switch (unit) {
                        case "s", "sec", "secs", "second", "seconds" -> value;
                        case "ms" -> value / 1000.0;
                        case "min", "mins", "minute", "minutes" -> value * 60.0;
                        default -> value;
                    };
                }
            }
        }
        throw new IllegalStateException(
                "Could not find 'Total time' line in the log. " +
                        "Is this a Maven build log with INFO-level output?");
    }

    // ---------- pass 1: collect per-module metrics ----------

    private Map<String, ModuleMetrics> parseModuleMetrics(List<String> lines) {
        Map<String, ModuleMetrics> map = new LinkedHashMap<>();
        String currentModule = null;

        for (String line : lines) {
            // module switch: [INFO] Building core 1.0-SNAPSHOT [2/4]
            Matcher buildingMatcher = BUILDING_MODULE_PATTERN.matcher(line);
            if (buildingMatcher.find()) {
                currentModule = buildingMatcher.group(1).trim();
                map.computeIfAbsent(currentModule, ModuleMetrics::new);
                continue;
            }

            if (currentModule == null) {
                // still before the first "Building ..." line
                continue;
            }

            ModuleMetrics metrics = map.get(currentModule);

            // plugin goal header
            Matcher pluginMatcher = PLUGIN_HEADER_PATTERN.matcher(line);
            if (pluginMatcher.find()) {
                String step = pluginMatcher.group(1).trim(); // e.g. "clean:3.4.0:clean"
                metrics.addPipelineStep(step);
                continue;
            }

            // compilation workload
            Matcher compileMatcher = COMPILE_PATTERN.matcher(line);
            if (compileMatcher.find()) {
                int files = Integer.parseInt(compileMatcher.group(1));
                String target = compileMatcher.group(2);

                if (target.contains("test-classes")) {
                    metrics.addTestSources(files);
                } else {
                    metrics.addMainSources(files);
                }
                continue;
            }

            // test stats (per test class, aggregated per module)
            Matcher testMatcher = TEST_RESULT_WITH_TIME_PATTERN.matcher(line);
            if (testMatcher.find()) {
                int run = Integer.parseInt(testMatcher.group(1));
                int failures = Integer.parseInt(testMatcher.group(2));
                int errors = Integer.parseInt(testMatcher.group(3));
                int skipped = Integer.parseInt(testMatcher.group(4));
                double time = Double.parseDouble(testMatcher.group(5));

                metrics.addTestStats(run, failures, errors, skipped, time);
            }
        }

        return map;
    }

    // ---------- pass 2: Reactor Summary + merge metrics ----------

    List<ModuleSummary> parseReactorSummary(List<String> lines,
                                            Map<String, ModuleMetrics> metricsByModule) {
        List<ModuleSummary> modules = new ArrayList<>();
        boolean inSummary = false;

        for (String line : lines) {
            if (!inSummary) {
                if (line.contains("Reactor Summary")) {
                    inSummary = true;
                }
                continue;
            }

            if (line.contains("BUILD SUCCESS") || line.contains("BUILD FAILURE")) {
                break;
            }

            Matcher m = MODULE_LINE_PATTERN.matcher(line);
            if (m.find()) {
                String moduleName = m.group(1).trim();
                double seconds = Double.parseDouble(m.group(2));

                ModuleMetrics metrics =
                        metricsByModule.computeIfAbsent(moduleName, ModuleMetrics::new);
                metrics.totalSeconds = seconds;

                modules.add(metrics.toSummary());
            }
        }

        if (modules.isEmpty()) {
            throw new IllegalStateException(
                    "Could not find any modules in 'Reactor Summary'. " +
                            "Multi-module Maven builds usually print it as '[INFO] Reactor Summary ...'. " +
                            "For single-module builds, the Reactor Summary section may be missing.");
        }

        return modules;
    }

    // ---------- internal accumulator ----------

    private static final class ModuleMetrics {
        final String name;
        double totalSeconds; // from Reactor Summary

        int testsRun;
        int failures;
        int errors;
        int skipped;
        double testTimeSeconds;

        int mainSourceFiles;
        int testSourceFiles;

        final List<String> pipelineSteps = new ArrayList<>();

        ModuleMetrics(String name) {
            this.name = name;
        }

        void addPipelineStep(String step) {
            pipelineSteps.add(step);
        }

        void addMainSources(int count) {
            mainSourceFiles += count;
        }

        void addTestSources(int count) {
            testSourceFiles += count;
        }

        void addTestStats(int run, int f, int e, int s, double time) {
            testsRun += run;
            failures += f;
            errors += e;
            skipped += s;
            testTimeSeconds += time;
        }

        ModuleSummary toSummary() {
            return new ModuleSummary(
                    name,
                    totalSeconds,
                    testsRun,
                    failures,
                    errors,
                    skipped,
                    testTimeSeconds,
                    mainSourceFiles,
                    testSourceFiles,
                    pipelineSteps
            );
        }
    }
}
