package com.buildanalyzer.core.health;

import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;

import java.util.List;

public class OverheadRule implements BuildHealthRule {

    // 非模块时间占比 > 25%：WARN；> 15%：INFO
    private static final double WARN_SHARE = 0.25;
    private static final double INFO_SHARE = 0.15;

    @Override
    public void apply(BuildSummary summary, List<BuildHealthHint> hints) {
        double total = summary.getTotalSeconds();
        if (total <= 0.0) return;

        double modules = summary.getModules().stream()
                .mapToDouble(ModuleSummary::getSeconds)
                .sum();

        double overhead = Math.max(0.0, total - modules);
        double share = overhead / total;

        if (share >= WARN_SHARE) {
            hints.add(new BuildHealthHint(
                    HealthSeverity.WARN,
                    "build",
                    String.format("Non-module overhead is %.3f s (%.1f%% of build). " +
                                    "Dependency resolution or lifecycle setup may be significant.",
                            overhead, share * 100.0)
            ));
        } else if (share >= INFO_SHARE) {
            hints.add(new BuildHealthHint(
                    HealthSeverity.INFO,
                    "build",
                    String.format("Non-module overhead is %.3f s (%.1f%% of build).",
                            overhead, share * 100.0)
            ));
        }
    }
}
