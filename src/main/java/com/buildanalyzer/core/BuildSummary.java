package com.buildanalyzer.core;

import java.util.List;

/**
 * Single overall Build Result
 * @author lhjls
 */
public class BuildSummary {
    private double totalSeconds;
    private List<ModuleSummary> modules;

    public BuildSummary(double totalSeconds, List<ModuleSummary> modules) {
        this.totalSeconds = totalSeconds;
        this.modules = modules;
    }

    public double getTotalSeconds() {
        return totalSeconds;
    }

    public List<ModuleSummary> getModules() {
        return modules;
    }
}
