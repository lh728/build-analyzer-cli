package com.buildanalyzer.core.model;

import java.util.Collections;
import java.util.List;

/**
 * Summary of a single module in a Maven build.
 */
public class ModuleSummary {

    private final String name;
    private final double seconds;
    private final int testsRun;
    private final int failures;
    private final int errors;
    private final int skipped;
    private final double testTimeSeconds;
    private final int mainSourceFiles;
    private final int testSourceFiles;
    private final List<String> pipelineSteps;

    /**
     * Minimal constructor: only name + total time.
     * Other metrics default to 0 / empty list.
     */
    public ModuleSummary(String name, double seconds) {
        this(name, seconds,
                0, 0, 0, 0, 0.0,
                0, 0,
                List.of());
    }

    /**
     * Full constructor with all metrics.
     */
    public ModuleSummary(String name,
                         double seconds,
                         int testsRun,
                         int failures,
                         int errors,
                         int skipped,
                         double testTimeSeconds,
                         int mainSourceFiles,
                         int testSourceFiles,
                         List<String> pipelineSteps) {
        this.name = name;
        this.seconds = seconds;
        this.testsRun = testsRun;
        this.failures = failures;
        this.errors = errors;
        this.skipped = skipped;
        this.testTimeSeconds = testTimeSeconds;
        this.mainSourceFiles = mainSourceFiles;
        this.testSourceFiles = testSourceFiles;
        this.pipelineSteps = pipelineSteps == null
                ? List.of()
                : Collections.unmodifiableList(List.copyOf(pipelineSteps));
    }

    public String getName() {
        return name;
    }

    public double getSeconds() {
        return seconds;
    }

    public int getTestsRun() {
        return testsRun;
    }

    public int getFailures() {
        return failures;
    }

    public int getErrors() {
        return errors;
    }

    public int getSkipped() {
        return skipped;
    }

    public double getTestTimeSeconds() {
        return testTimeSeconds;
    }

    public int getMainSourceFiles() {
        return mainSourceFiles;
    }

    public int getTestSourceFiles() {
        return testSourceFiles;
    }

    public List<String> getPipelineSteps() {
        return pipelineSteps;
    }
}
