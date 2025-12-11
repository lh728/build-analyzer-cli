package com.buildanalyzer.core.parser;

import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author lhjls
 */
public class MavenLogParser {

    // [INFO] Total time:  8.294 s
    private static final Pattern TOTAL_TIME_PATTERN =
            Pattern.compile("Total time:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*([a-zA-Z]+)");

    // [INFO] webapp ..................................... SUCCESS [  1.548 s]
    private static final Pattern MODULE_LINE_PATTERN =
            Pattern.compile("\\[INFO]\\s+(.+?)\\s+.*\\[\\s*([0-9]+(?:\\.[0-9]+)?)\\s*s]");

    public BuildSummary parse(Path logPath) throws IOException {
        List<String> lines = Files.readAllLines(logPath);
        double totalSeconds = parseTotalTime(lines);
        List<ModuleSummary> modules = parseReactorSummary(lines);
        return new BuildSummary(totalSeconds, modules);
    }

    /**
     * parse total time from the log
     */
    double parseTotalTime(List<String> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line.contains("Total time:")) {
                Matcher m = TOTAL_TIME_PATTERN.matcher(line);
                if (m.find()) {
                    double value = Double.parseDouble(m.group(1));
                    String unit = m.group(2).toLowerCase();
                    return switch (unit) {
                        case "s", "sec", "secs", "second", "seconds" -> value;
                        case "ms" -> value / 1000.0;
                        case "min", "mins", "minute", "minutes" -> value * 60.0;
                        default -> value;
                    };
                }
            }
        }
        throw new IllegalStateException(
                "Could not find 'Total time' line in the log. " +
                        "Is this a Maven build log with INFO-level output?");
    }

    /**
     * parse Reactor Summary each module time consume
     */
    List<ModuleSummary> parseReactorSummary(List<String> lines) {
        List<ModuleSummary> modules = new ArrayList<>();
        boolean inSummary = false;

        for (String line : lines) {
            if (!inSummary) {
                if (line.contains("Reactor Summary")) {
                    inSummary = true;
                }
                continue;
            }

            if (line.contains("BUILD SUCCESS")
                    || line.contains("BUILD FAILURE")) {
                break;
            }

            Matcher m = MODULE_LINE_PATTERN.matcher(line);
            if (m.find()) {
                String moduleName = m.group(1).trim();
                double seconds = Double.parseDouble(m.group(2));
                modules.add(new ModuleSummary(moduleName, seconds));
            }
        }

        if (modules.isEmpty()) {
            throw new IllegalStateException(
                    "Could not find any modules in 'Reactor Summary'. " +
                            "Multi-module Maven builds usually print it as '[INFO] Reactor Summary ...'. " +
                            "For single-module builds, the Reactor Summary section may be missing.");
        }

        return modules;
    }
}
