package com.buildanalyzer.core;

import java.util.Locale;

/**
 * Parses command-line arguments for the Build Analyzer CLI.
 * @author lhjls
 */
public final class CliArgumentParser {

    private CliArgumentParser() {
        // utility class
    }

    public static CliOptions parse(String[] args) {
        if (args.length == 0) {
            printUsageAndExit();
        }

        boolean jsonOutput = false;
        boolean prettyJson = false;
        String logFile = null;

        for (String arg : args) {
            if (arg.startsWith("--")) {
                // long options: --json, --pretty
                switch (arg) {
                    case "--json" -> jsonOutput = true;
                    case "--pretty" -> prettyJson = true;
                    default -> {
                        System.err.println("Unknown option: " + arg);
                        printUsageAndExit();
                    }
                }
            } else if (arg.startsWith("-")) {
                // short options, allow combination: -j, -p, -jp, -pj, -JP ...
                String flags = arg.substring(1).toLowerCase(Locale.ROOT);
                if (flags.isEmpty()) {
                    System.err.println("Invalid option: " + arg);
                    printUsageAndExit();
                }
                for (int i = 0; i < flags.length(); i++) {
                    char c = flags.charAt(i);
                    switch (c) {
                        case 'j' -> jsonOutput = true;
                        case 'p' -> prettyJson = true;
                        default -> {
                            System.err.println("Unknown short option: -" + c);
                            printUsageAndExit();
                        }
                    }
                }
            } else {
                // positional argument: log file (only one allowed)
                if (logFile != null) {
                    System.err.println("Too many positional arguments (only one <maven-log-file> is allowed).");
                    printUsageAndExit();
                }
                logFile = arg;
            }
        }

        if (logFile == null) {
            System.err.println("Missing <maven-log-file> argument.");
            printUsageAndExit();
        }

        // pretty only makes sense with json
        if (prettyJson && !jsonOutput) {
            System.err.println("--pretty / -p can only be used together with --json / -j.");
            printUsageAndExit();
        }

        return new CliOptions(jsonOutput, prettyJson, logFile);
    }

    public static void printUsageAndExit() {
        System.err.println("Usage: build-analyzer [options] <maven-log-file>");
        System.err.println("Options:");
        System.err.println("  -j, --json       Output JSON instead of text");
        System.err.println("  -p, --pretty     Pretty-print JSON (requires -j/--json)");
        System.exit(1);
    }
}
