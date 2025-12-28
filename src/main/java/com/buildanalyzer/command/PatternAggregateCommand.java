package com.buildanalyzer.command;

import com.buildanalyzer.cli.CliOptions;
import com.buildanalyzer.output.AggregatedTextPrinter;
import com.buildanalyzer.output.JsonOutputWriter;
import com.buildanalyzer.util.LogFileResolver;
import com.buildanalyzer.core.aggregate.AggregatedSummary;
import com.buildanalyzer.core.aggregate.BuildAggregator;
import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.parser.MavenLogParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI command: aggregate log files by glob pattern, e.g. "ci-logs/build-*.log".
 */
public class PatternAggregateCommand implements CliCommand {

    private final LogFileResolver fileResolver = new LogFileResolver();
    private final MavenLogParser parser = new MavenLogParser();
    private final BuildAggregator aggregator = new BuildAggregator();
    private final AggregatedTextPrinter textPrinter = new AggregatedTextPrinter();
    private final JsonOutputWriter jsonWriter = new JsonOutputWriter();

    @Override
    public void execute(CliOptions options) {
        String raw = options.aggregatePattern();
        int lastSlash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));

        Path dir;
        String filePattern;

        if (lastSlash >= 0) {
            String dirPart = raw.substring(0, lastSlash);
            String patternPart = raw.substring(lastSlash + 1);

            dir = dirPart.isEmpty() ? Paths.get(".") : Paths.get(dirPart);
            filePattern = patternPart;
        } else {
            dir = Paths.get(".");
            filePattern = raw;
        }

        if (!Files.exists(dir)) {
            System.err.println("ERROR: Directory for pattern not found: " + dir.toAbsolutePath());
            System.exit(2);
        }
        if (!Files.isDirectory(dir)) {
            System.err.println("ERROR: Not a directory for pattern: " + dir.toAbsolutePath());
            System.exit(2);
        }

        List<Path> logFiles;
        try {
            logFiles = fileResolver.listLogFilesByPattern(dir, filePattern);
        } catch (IOException e) {
            System.err.println("ERROR: Failed to list files for pattern '" + filePattern +
                    "' in directory: " + dir.toAbsolutePath() + " -> " + e.getMessage());
            System.exit(4);
            return; // unreachable, but needed for compiler
        }

        if (logFiles.isEmpty()) {
            System.err.println("ERROR: No files matching pattern '" + filePattern +
                    "' in directory: " + dir.toAbsolutePath());
            System.exit(7);
        }

        aggregateAndPrint("PATTERN", logFiles, options);
    }

    private void aggregateAndPrint(String modeLabel,
                                   List<Path> logFiles,
                                   CliOptions options) {
        List<BuildSummary> summaries = new ArrayList<>();

        for (Path log : logFiles) {
            try {
                summaries.add(parser.parse(log));
            } catch (IllegalStateException | IOException e) {
                System.err.println("WARN: Skipping log '" + log + "': " + e.getMessage());
            }
        }

        if (summaries.isEmpty()) {
            System.err.println("ERROR: No valid Maven builds found in the selected logs.");
            System.exit(8);
        }

        AggregatedSummary aggregated = aggregator.aggregate(summaries);

        if (options.jsonOutput()) {
            jsonWriter.printAggregated(modeLabel, logFiles, aggregated, options.prettyJson());
        } else {
            textPrinter.print(modeLabel, logFiles, aggregated);
        }
    }
}

