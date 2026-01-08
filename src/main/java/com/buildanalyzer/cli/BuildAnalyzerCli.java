package com.buildanalyzer.cli;

import com.buildanalyzer.command.*;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Entry point for the Build Analyzer CLI.
 *
 * Responsibilities:
 *   - parse CLI options
 *   - select the appropriate command for the chosen Mode
 *   - handle top-level error codes
 *
 * It no longer contains business logic for parsing logs or printing reports.
 */
public class BuildAnalyzerCli {

    private static final Map<Mode, CliCommand> COMMANDS = new EnumMap<>(Mode.class);

    static {
        COMMANDS.put(Mode.SINGLE_LOG, new SingleLogCommand());
        COMMANDS.put(Mode.DIRECTORY, new DirectoryAggregateCommand());
        COMMANDS.put(Mode.PATTERN, new PatternAggregateCommand());
        COMMANDS.put(Mode.CLEAN_INSTALL, new CleanInstallCommand());
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalStateException e) {
            // business exception（ex. Total time / Reactor Summary）
            System.err.println("ERROR: " + e.getMessage());
            System.exit(3);
        } catch (IOException e) {
            System.err.println("ERROR: I/O error: " + e.getMessage());
            System.exit(4);
        } catch (Exception e) {
            // unexpected exception
            System.err.println("Unexpected error: " + e.getClass().getSimpleName()
                    + ": " + (e.getMessage() == null ? "" : e.getMessage()));
            e.printStackTrace(System.err);
            System.exit(99);
        }
    }

    private static void run(String[] args) throws Exception {
        CliOptions options = CliArgumentParser.parse(args);

        CliCommand command = COMMANDS.get(options.mode());
        if (command == null) {
            throw new IllegalStateException("Unsupported mode: " + options.mode());
        }

        command.execute(options);
    }
}
