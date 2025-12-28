package com.buildanalyzer.command;

import com.buildanalyzer.cli.CliOptions;
import com.buildanalyzer.output.JsonOutputWriter;
import com.buildanalyzer.output.SingleBuildTextPrinter;
import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.parser.MavenLogParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI command: analyze a single Maven log file.
 */
public class SingleLogCommand implements CliCommand {

    private final MavenLogParser parser = new MavenLogParser();
    private final SingleBuildTextPrinter textPrinter = new SingleBuildTextPrinter();
    private final JsonOutputWriter jsonWriter = new JsonOutputWriter();

    @Override
    public void execute(CliOptions options) throws Exception {
        Path logPath = Paths.get(options.logFile());

        if (!Files.exists(logPath)) {
            System.err.println("ERROR: File not found: " + logPath.toAbsolutePath());
            System.exit(2);
        }

        BuildSummary summary = parser.parse(logPath);

        if (options.jsonOutput()) {
            jsonWriter.printSingleBuild(summary, options.prettyJson());
        } else {
            textPrinter.print(logPath, summary);
        }
    }
}

