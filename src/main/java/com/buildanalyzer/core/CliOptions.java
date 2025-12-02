package com.buildanalyzer.core;

/**
 * Parsed command-line options for the Build Analyzer CLI.
 */
public record CliOptions(
        boolean jsonOutput,
        boolean prettyJson,
        String logFile
) {
}
