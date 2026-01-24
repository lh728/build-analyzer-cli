package com.buildanalyzer.core.health;

import com.buildanalyzer.core.model.BuildSummary;

import java.util.ArrayList;
import java.util.List;

/**
 * 执行一组 BuildHealthRule，并返回所有提示。
 */
public class BuildHealthEvaluator {

    private final List<BuildHealthRule> rules;

    public BuildHealthEvaluator() {
        this.rules = List.of(
                new TotalTimeRule(),
                new OverheadRule(),
                new HotModuleRule(),
                new TestFailuresRule(),
                new TestRatioRule(),
                new UntestedLargeModuleRule()
        );
    }

    public List<BuildHealthHint> evaluate(BuildSummary summary) {
        List<BuildHealthHint> hints = new ArrayList<>();
        for (BuildHealthRule rule : rules) {
            rule.apply(summary, hints);
        }
        return hints;
    }
}

