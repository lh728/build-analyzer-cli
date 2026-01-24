package com.buildanalyzer.core.health;

import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;

import java.util.List;

public class TestRatioRule implements BuildHealthRule {

    // 测试时间 > 50%：WARN； > 30%：INFO
    private static final double WARN_SHARE = 0.50;
    private static final double INFO_SHARE = 0.30;

    @Override
    public void apply(BuildSummary summary, List<BuildHealthHint> hints) {
        for (ModuleSummary m : summary.getModules()) {
            double moduleSeconds = m.getSeconds();
            double testSeconds = m.getTestTimeSeconds();

            if (moduleSeconds <= 0.0 || testSeconds <= 0.0) {
                continue;
            }

            double share = testSeconds / moduleSeconds;

            if (share >= WARN_SHARE) {
                hints.add(new BuildHealthHint(
                        HealthSeverity.WARN,
                        m.getName(),
                        String.format("Tests account for %.1f%% of module '%s' time " +
                                        "(%.3f s out of %.3f s). Consider speeding up or splitting tests.",
                                share * 100.0, m.getName(), testSeconds, moduleSeconds)
                ));
            } else if (share >= INFO_SHARE) {
                hints.add(new BuildHealthHint(
                        HealthSeverity.INFO,
                        m.getName(),
                        String.format("Tests account for %.1f%% of module '%s' time " +
                                        "(%.3f s out of %.3f s).",
                                share * 100.0, m.getName(), testSeconds, moduleSeconds)
                ));
            }
        }
    }
}
