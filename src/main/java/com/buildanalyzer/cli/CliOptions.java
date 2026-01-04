package com.buildanalyzer.cli;

import java.util.List;

public record CliOptions(
        Mode mode,
        boolean jsonOutput,
        boolean prettyJson,

        // for SINGLE_LOG
        String logFile,

        // for DIRECTORY
        String directory,

        // for PATTERN
        String aggregatePattern,

        // for CLEAN_INSTALL
        String projectDir,           // may be null -> default "."
        List<String> extraMavenArgs  // never null; use List.of() if empty
) {}

