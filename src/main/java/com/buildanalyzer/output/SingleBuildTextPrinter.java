package com.buildanalyzer.output;

import com.buildanalyzer.core.health.BuildHealthEvaluator;
import com.buildanalyzer.core.health.BuildHealthHint;
import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Consumer;

/**
 * Renders a single BuildSummary as human-readable text.
 */
public class SingleBuildTextPrinter {

    public void print(Path logPath, BuildSummary summary) {
        double totalBuild = summary.getTotalSeconds();
        double totalModules = summary.getModules().stream()
                .mapToDouble(ModuleSummary::getSeconds)
                .sum();
        double overhead = Math.max(0.0, totalBuild - totalModules);

        System.out.println("=== Build Analyzer CLI ===");
        System.out.println("Log file : " + logPath);
        System.out.println();

        System.out.printf("Total build time   : %.3f s%n", totalBuild);
        System.out.printf("Modules total time : %.3f s (%.1f%% of build)%n",
                totalModules, totalModules / totalBuild * 100.0);
        System.out.printf("Other / overhead   : %.3f s (%.1f%% of build)%n%n",
                overhead, overhead / totalBuild * 100.0);

        System.out.println("Modules by time (share of whole build):");

        summary.getModules().stream()
                .sorted(Comparator.comparingDouble(ModuleSummary::getSeconds).reversed())
                .forEachOrdered(new Consumer<>() {
                    int index = 0;

                    @Override
                    public void accept(ModuleSummary m) {
                        index++;
                        double percentOfBuild = (m.getSeconds() / totalBuild) * 100.0;
                        System.out.printf(
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
                    double percentOfBuild = slowest.getSeconds() / totalBuild * 100.0;
                    System.out.printf("Slowest module: %s (%.3f s, %.1f%% of build)%n",
                            slowest.getName(), slowest.getSeconds(), percentOfBuild);
                });

        // --- Test breakdown per module ---

        System.out.println();
        System.out.println("Test breakdown per module:");

        for (ModuleSummary m : summary.getModules()) {
            if (m.getTestsRun() == 0 && m.getTestTimeSeconds() <= 0.0) {
                System.out.printf("  %s: no tests detected%n", m.getName());
            } else {
                double pctOfModule = m.getSeconds() > 0.0
                        ? (m.getTestTimeSeconds() / m.getSeconds()) * 100.0
                        : 0.0;
                System.out.printf(
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
            System.out.printf(
                    "  %s: main %d, test %d%n",
                    m.getName(),
                    m.getMainSourceFiles(),
                    m.getTestSourceFiles()
            );
        }

        // --- Health hints ---
        printHealthHints(summary);

    }

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
                // module 作用域
                System.out.printf("  %s [%s] %s%n", label, scope, hint.message());
            } else {
                // 整体构建
                System.out.printf("  %s %s%n", label, hint.message());
            }
        }
    }


}

