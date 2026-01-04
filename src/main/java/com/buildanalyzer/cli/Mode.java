package com.buildanalyzer.cli;


/**
 * High-level operation mode of the CLI.
 * @author lhjls
 */
public enum Mode {
    /**
     * Analyze a single Maven log file.
     * Default when only <maven-log-file> is provided.
     */
    SINGLE_LOG,

    /**
     * Analyze all log files under a directory (e.g. --dir ci-logs/).
     */
    DIRECTORY,

    /**
     * Analyze all log files matching a glob pattern
     * (e.g. --aggregate ci-logs/build-*.log).
     */
    PATTERN,
    /**
     * run 'mvn clean install' then analyze captured log
     */
    CLEAN_INSTALL
}
