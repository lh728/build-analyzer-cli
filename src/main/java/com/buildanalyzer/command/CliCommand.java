package com.buildanalyzer.command;

import com.buildanalyzer.cli.CliOptions;

/**
 * A unit of work the CLI can execute, e.g.:
 * - analyze a single log file
 * - aggregate all logs in a directory
 * - aggregate logs by glob pattern
 */
public interface CliCommand {

    /**
     * Execute this CLI command.
     *
     * Implementations are allowed to call System.exit(...) on fatal errors,
     * since this is a CLI application.
     */
    void execute(CliOptions options) throws Exception;
}

