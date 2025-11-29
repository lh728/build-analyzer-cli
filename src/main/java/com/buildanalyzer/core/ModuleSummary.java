package com.buildanalyzer.core;

/**
 * Single Module Build Summary
 * @author lhjls
 */
public class ModuleSummary {
    private String name;
    private double seconds;

    public ModuleSummary(String name, double seconds) {
        this.name = name;
        this.seconds = seconds;
    }

    public String getName() {
        return name;
    }

    public double getSeconds() {
        return seconds;
    }
}
