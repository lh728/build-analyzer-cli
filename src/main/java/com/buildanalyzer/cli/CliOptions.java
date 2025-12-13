package com.buildanalyzer.cli;

/**
 * Parsed command-line options for the Build Analyzer CLI.
 */
public record CliOptions(
        boolean jsonOutput,
        boolean prettyJson,
        Mode mode,
        String logFile,          // for SINGLE_LOG
        String directory,        // for DIRECTORY
        String aggregatePattern  // for PATTERN
) {}
