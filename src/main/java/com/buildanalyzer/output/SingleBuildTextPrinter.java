package com.buildanalyzer.output;

import com.buildanalyzer.core.health.BuildHealthEvaluator;
import com.buildanalyzer.core.health.BuildHealthHint;
import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Renders a single BuildSummary as human-readable text.
 *
 * Parallel build handling (Maven -T):
 * - If log indicates MultiThreadedBuilder, we degrade:
 *   * Keep wall clock total time + Reactor Summary module durations
 *   * Disable per-module test/compile attribution (interleaved logs)
 *   * Show build-wide totals for tests/compile
 */
public class SingleBuildTextPrinter {

    private static final double EPS = 1e-9;

    public void print(Path logPath, BuildSummary summary) {
        boolean parallel = logIndicatesParallelBuild(logPath);

        System.out.println("=== Build Analyzer CLI ===");
        System.out.println("Log file : " + logPath);
        System.out.println();

        if (parallel) {
            printParallelDegraded(summary);
        } else {
            printSerial(summary);
            printHealthHints(summary); // keep your existing evaluator for serial logs
        }
    }

    // ---------------- Serial (original behavior) ----------------

    private void printSerial(BuildSummary summary) {
        double totalBuild = summary.getTotalSeconds();
        double totalModules = summary.getModules().stream()
                .mapToDouble(ModuleSummary::getSeconds)
                .sum();
        double overhead = Math.max(0.0, totalBuild - totalModules);

        System.out.printf(Locale.ROOT, "Total build time   : %.3f s%n", totalBuild);

        if (totalBuild > EPS) {
            System.out.printf(Locale.ROOT, "Modules total time : %.3f s (%.1f%% of build)%n",
                    totalModules, totalModules / totalBuild * 100.0);
            System.out.printf(Locale.ROOT, "Other / overhead   : %.3f s (%.1f%% of build)%n%n",
                    overhead, overhead / totalBuild * 100.0);
        } else {
            System.out.printf(Locale.ROOT, "Modules total time : %.3f s%n", totalModules);
            System.out.printf(Locale.ROOT, "Other / overhead   : %.3f s%n%n", overhead);
        }

        System.out.println("Modules by time (share of whole build):");

        summary.getModules().stream()
                .sorted(Comparator.comparingDouble(ModuleSummary::getSeconds).reversed())
                .forEachOrdered(new java.util.function.Consumer<>() {
                    int index = 0;

                    @Override
                    public void accept(ModuleSummary m) {
                        index++;
                        double percentOfBuild = (totalBuild > EPS)
                                ? (m.getSeconds() / totalBuild) * 100.0
                                : 0.0;
                        System.out.printf(Locale.ROOT,
                                "  %d) %-15s %6.3f s  (%4.1f%% of build)%n",
                                index,
                                m.getName(),
                                m.getSeconds(),
                                percentOfBuild
                        );
                    }
                });

        System.out.println();

        summary.getModules().stream()
                .max(Comparator.comparingDouble(ModuleSummary::getSeconds))
                .ifPresent(slowest -> {
                    double percentOfBuild = (totalBuild > EPS)
                            ? slowest.getSeconds() / totalBuild * 100.0
                            : 0.0;
                    System.out.printf(Locale.ROOT,
                            "Slowest module: %s (%.3f s, %.1f%% of build)%n",
                            slowest.getName(), slowest.getSeconds(), percentOfBuild);
                });

        // --- Test breakdown per module ---
        System.out.println();
        System.out.println("Test breakdown per module:");

        for (ModuleSummary m : summary.getModules()) {
            if (m.getTestsRun() == 0 && m.getTestTimeSeconds() <= 0.0) {
                System.out.printf(Locale.ROOT, "  %s: no tests detected%n", m.getName());
            } else {
                double pctOfModule = m.getSeconds() > EPS
                        ? (m.getTestTimeSeconds() / m.getSeconds()) * 100.0
                        : 0.0;
                System.out.printf(Locale.ROOT,
                        "  %s: tests %d (F:%d, E:%d, S:%d) in %.3f s (%.1f%% of module time)%n",
                        m.getName(),
                        m.getTestsRun(),
                        m.getFailures(),
                        m.getErrors(),
                        m.getSkipped(),
                        m.getTestTimeSeconds(),
                        pctOfModule
                );
            }
        }

        // --- Compilation workload ---
        System.out.println();
        System.out.println("Compilation workload (source files):");

        for (ModuleSummary m : summary.getModules()) {
            System.out.printf(Locale.ROOT,
                    "  %s: main %d, test %d%n",
                    m.getName(),
                    m.getMainSourceFiles(),
                    m.getTestSourceFiles()
            );
        }
    }

    // ---------------- Parallel (degraded) ----------------

    private void printParallelDegraded(BuildSummary summary) {
        System.out.println("NOTE: Parallel build detected (MultiThreadedBuilder / -T).");
        System.out.println("      In parallel builds, module times overlap, so some per-module metrics are disabled.");
        System.out.println();

        double wall = summary.getTotalSeconds();
        double work = summary.getModules().stream().mapToDouble(ModuleSummary::getSeconds).sum();
        double maxModule = summary.getModules().stream().mapToDouble(ModuleSummary::getSeconds).max().orElse(0.0);
        double overlap = Math.max(0.0, work - wall);

        System.out.printf(Locale.ROOT, "Wall clock total time : %.3f s%n", wall);

        if (wall > EPS) {
            System.out.printf(Locale.ROOT,
                    "Module work (sum of module durations): %.3f s  (%.2fx wall clock)%n",
                    work, work / wall);
            System.out.printf(Locale.ROOT,
                    "Critical-path estimate (max module)  : %.3f s  (%.1f%% of wall clock)%n",
                    maxModule, (maxModule / wall) * 100.0);
        } else {
            System.out.printf(Locale.ROOT,
                    "Module work (sum of module durations): %.3f s%n", work);
            System.out.printf(Locale.ROOT,
                    "Critical-path estimate (max module)  : %.3f s%n", maxModule);
        }

        System.out.printf(Locale.ROOT,
                "Estimated overlap / parallelism gain : %.3f s  (work - wall)%n%n", overlap);

        // modules list
        System.out.println("Modules by duration (Reactor Summary):");

        List<ModuleSummary> sorted = summary.getModules().stream()
                .sorted(Comparator.comparingDouble(ModuleSummary::getSeconds).reversed())
                .toList();

        int idx = 0;
        for (ModuleSummary m : sorted) {
            idx++;
            double pctWall = (wall > EPS) ? (m.getSeconds() / wall) * 100.0 : 0.0;
            double pctWork = (work > EPS) ? (m.getSeconds() / work) * 100.0 : 0.0;

            System.out.printf(Locale.ROOT,
                    "  %d) %-15s %6.3f s   (%4.1f%% of wall, %4.1f%% of work)%n",
                    idx, m.getName(), m.getSeconds(), pctWall, pctWork
            );
        }

        System.out.println();

        // slowest module(s) - Java 17 compatible (no List#getFirst)
        if (!sorted.isEmpty()) {
            double max = sorted.get(0).getSeconds();
            List<String> slowestNames = sorted.stream()
                    .filter(m -> Math.abs(m.getSeconds() - max) < 1e-6)
                    .map(ModuleSummary::getName)
                    .toList();

            if (slowestNames.size() == 1) {
                System.out.printf(Locale.ROOT, "Slowest module: %s (%.3f s)%n", slowestNames.get(0), max);
            } else {
                System.out.printf(Locale.ROOT, "Slowest module(s): %s (%.3f s each)%n",
                        String.join(", ", slowestNames), max);
            }
        }

        // build-wide totals (safer than per-module attribution)
        int tests = summary.getModules().stream().mapToInt(ModuleSummary::getTestsRun).sum();
        int failures = summary.getModules().stream().mapToInt(ModuleSummary::getFailures).sum();
        int errors = summary.getModules().stream().mapToInt(ModuleSummary::getErrors).sum();
        int skipped = summary.getModules().stream().mapToInt(ModuleSummary::getSkipped).sum();
        double testTime = summary.getModules().stream().mapToDouble(ModuleSummary::getTestTimeSeconds).sum();

        System.out.println();
        System.out.println("Tests (build-wide, not per module):");
        if (tests == 0 && testTime <= EPS) {
            System.out.println("  (no tests detected)");
        } else {
            System.out.printf(Locale.ROOT,
                    "  tests %d (F:%d, E:%d, S:%d) in %.3f s%n",
                    tests, failures, errors, skipped, testTime);
        }

        int mainSources = summary.getModules().stream().mapToInt(ModuleSummary::getMainSourceFiles).sum();
        int testSources = summary.getModules().stream().mapToInt(ModuleSummary::getTestSourceFiles).sum();

        System.out.println();
        System.out.println("Compilation workload (build-wide, not per module):");
        System.out.printf(Locale.ROOT, "  main %d, test %d%n", mainSources, testSources);

        // parallel-friendly hints
        System.out.println();
        System.out.println("Build health hints:");
        System.out.println("  [INFO] Parallel build detected. Per-module test/compile attribution is disabled to avoid incorrect data.");

        // critical-path candidates within 95% of max
        double threshold = maxModule * 0.95;
        List<String> candidates = summary.getModules().stream()
                .filter(m -> m.getSeconds() + 1e-6 >= threshold)
                .sorted(Comparator.comparingDouble(ModuleSummary::getSeconds).reversed())
                .map(ModuleSummary::getName)
                .toList();

        if (!candidates.isEmpty()) {
            List<String> top = candidates.size() > 5 ? candidates.subList(0, 5) : candidates;
            System.out.printf(Locale.ROOT,
                    "  [WARN] Critical-path candidates: %s (~%.3f s). Speeding up any of them may reduce wall time.%n",
                    String.join(", ", top),
                    maxModule
            );
        }
    }

    // ---------------- Original health hints (serial only) ----------------

    private void printHealthHints(BuildSummary summary) {
        BuildHealthEvaluator evaluator = new BuildHealthEvaluator();
        var hints = evaluator.evaluate(summary);

        System.out.println();
        System.out.println("Build health hints:");

        if (hints.isEmpty()) {
            System.out.println("  (no issues detected by current rules)");
            return;
        }

        for (BuildHealthHint hint : hints) {
            String label = switch (hint.severity()) {
                case CRITICAL -> "[CRITICAL]";
                case WARN -> "[WARN]";
                case INFO -> "[INFO]";
            };

            String scope = hint.scope();
            if (scope != null && !scope.isBlank() && !"build".equals(scope)) {
                System.out.printf("  %s [%s] %s%n", label, scope, hint.message());
            } else {
                System.out.printf("  %s %s%n", label, hint.message());
            }
        }
    }

    private static boolean logIndicatesParallelBuild(Path logPath) {
        try (var lines = Files.lines(logPath)) {
            return lines.anyMatch(l -> l != null && l.contains("MultiThreadedBuilder"));
        } catch (IOException e) {
            return false;
        }
    }
}
