package com.buildanalyzer.core.health;

import com.buildanalyzer.core.model.BuildSummary;

import java.util.List;

public interface BuildHealthRule {

    /**
     * 根据 BuildSummary 产生 0..N 条提示，追加到 hints 里。
     */
    void apply(BuildSummary summary, List<BuildHealthHint> hints);
}
