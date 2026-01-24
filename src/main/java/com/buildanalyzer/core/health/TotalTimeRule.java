package com.buildanalyzer.core.health;

import com.buildanalyzer.core.model.BuildSummary;

import java.util.List;

public class TotalTimeRule implements BuildHealthRule {

    // > 5 min：WARN； > 2 min：INFO
    private static final double WARN_SECONDS = 300.0;
    private static final double INFO_SECONDS = 120.0;

    @Override
    public void apply(BuildSummary summary, List<BuildHealthHint> hints) {
        double total = summary.getTotalSeconds();

        if (total >= WARN_SECONDS) {
            hints.add(new BuildHealthHint(
                    HealthSeverity.WARN,
                    "build",
                    String.format("Total build time is %.1f s (%.1f min). Consider optimizing hotspots.",
                            total, total / 60.0)
            ));
        } else if (total >= INFO_SECONDS) {
            hints.add(new BuildHealthHint(
                    HealthSeverity.INFO,
                    "build",
                    String.format("Total build time is %.1f s (%.1f min). Might be worth watching over time.",
                            total, total / 60.0)
            ));
        }
    }
}
