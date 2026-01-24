package com.buildanalyzer.core.health;

import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;

import java.util.Comparator;
import java.util.List;

public class HotModuleRule implements BuildHealthRule {

    // 单个模块占总时间比例 > 40%：WARN；> 25%：INFO
    private static final double WARN_SHARE = 0.40;
    private static final double INFO_SHARE = 0.25;

    @Override
    public void apply(BuildSummary summary, List<BuildHealthHint> hints) {
        double total = summary.getTotalSeconds();
        if (total <= 0.0) return;

        List<ModuleSummary> modules = summary.getModules().stream()
                .sorted(Comparator.comparingDouble(ModuleSummary::getSeconds).reversed())
                .toList();

        for (ModuleSummary m : modules) {
            double share = m.getSeconds() / total;

            if (share >= WARN_SHARE) {
                hints.add(new BuildHealthHint(
                        HealthSeverity.WARN,
                        m.getName(),
                        String.format("Module '%s' takes %.1f%% of total build time (%.3f s). " +
                                        "It is a clear hotspot.",
                                m.getName(), share * 100.0, m.getSeconds())
                ));
            } else if (share >= INFO_SHARE) {
                hints.add(new BuildHealthHint(
                        HealthSeverity.INFO,
                        m.getName(),
                        String.format("Module '%s' takes %.1f%% of total build time (%.3f s).",
                                m.getName(), share * 100.0, m.getSeconds())
                ));
            }
        }
    }
}
