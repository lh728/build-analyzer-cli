package com.buildanalyzer.core.health;

import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;

import java.util.List;

public class UntestedLargeModuleRule implements BuildHealthRule {

    // main 源文件数量达到一定规模但完全没跑测试
    private static final int WARN_MAIN_SOURCES = 50;
    private static final int INFO_MAIN_SOURCES = 10;

    @Override
    public void apply(BuildSummary summary, List<BuildHealthHint> hints) {
        for (ModuleSummary m : summary.getModules()) {
            int mainSources = m.getMainSourceFiles();
            int testsRun = m.getTestsRun();
            int testSources = m.getTestSourceFiles();

            if (mainSources >= WARN_MAIN_SOURCES && testsRun == 0) {
                hints.add(new BuildHealthHint(
                        HealthSeverity.WARN,
                        m.getName(),
                        String.format("Module '%s' has %d main source files but no tests were executed.",
                                m.getName(), mainSources)
                ));
            } else if (mainSources >= INFO_MAIN_SOURCES && testSources == 0) {
                hints.add(new BuildHealthHint(
                        HealthSeverity.INFO,
                        m.getName(),
                        String.format("Module '%s' has %d main source files but no test sources were compiled.",
                                m.getName(), mainSources)
                ));
            }
        }
    }
}

