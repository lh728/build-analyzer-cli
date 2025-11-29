package com.buildanalyzer.core;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * @author lhjls
 */
public class BuildAnalyzerCli {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: build-analyzer <maven-log-file>");
            System.exit(1);
        }

        Path logPath = Paths.get(args[0]);
        MavenLogParser parser = new MavenLogParser();
        BuildSummary summary = parser.parse(logPath);

        System.out.printf("Build total time: %.3f s%n", summary.getTotalSeconds());
        System.out.println("Modules:");
        summary.getModules().stream()
                .sorted(Comparator.comparingDouble(ModuleSummary::getSeconds).reversed())
                .forEach(m -> System.out.printf(" - %s : %.3f s%n", m.getName(), m.getSeconds()));
    }
}

