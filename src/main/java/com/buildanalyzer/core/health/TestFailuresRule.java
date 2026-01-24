package com.buildanalyzer.core.health;

import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;

import java.util.List;

public class TestFailuresRule implements BuildHealthRule {

    @Override
    public void apply(BuildSummary summary, List<BuildHealthHint> hints) {
        for (ModuleSummary m : summary.getModules()) {
            if (m.getFailures() > 0 || m.getErrors() > 0) {
                hints.add(new BuildHealthHint(
                        HealthSeverity.CRITICAL,
                        m.getName(),
                        String.format("Tests in module '%s' have %d failures and %d errors.",
                                m.getName(), m.getFailures(), m.getErrors())
                ));
            }
        }
    }
}
