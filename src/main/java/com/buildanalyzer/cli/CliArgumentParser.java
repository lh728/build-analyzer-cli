package com.buildanalyzer.cli;

import java.util.Locale;

/**
 * Parses command-line arguments for the Build Analyzer CLI.
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

        Mode mode = null;
        String logFile = null;
        String directory = null;
        String aggregatePattern = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            // ---- long options: --json, --pretty, --dir, --aggregate ----
            if (arg.startsWith("--")) {
                switch (arg) {
                    case "--json" -> jsonOutput = true;
                    case "--pretty" -> prettyJson = true;
                    case "--dir" -> {
                        if (mode != null) {
                            fail("Only one of <maven-log-file>, --dir, or --aggregate can be used.");
                        }
                        if (i + 1 >= args.length) {
                            fail("--dir requires a directory path.");
                        }
                        mode = Mode.DIRECTORY;
                        directory = args[++i];
                    }
                    case "--aggregate" -> {
                        if (mode != null) {
                            fail("Only one of <maven-log-file>, --dir, or --aggregate can be used.");
                        }
                        if (i + 1 >= args.length) {
                            fail("--aggregate requires a glob pattern.");
                        }
                        mode = Mode.PATTERN;
                        aggregatePattern = args[++i];
                    }
                    default -> {
                        fail("Unknown option: " + arg);
                    }
                }
                continue;
            }

            // ---- short options / flags ----
            if (arg.startsWith("-")) {
                String lower = arg.toLowerCase(Locale.ROOT);

                // combined -jp / -pj for json + pretty
                if ("-jp".equals(lower) || "-pj".equals(lower)) {
                    jsonOutput = true;
                    prettyJson = true;
                    continue;
                }

                switch (lower) {
                    case "-j" -> jsonOutput = true;
                    case "-p" -> prettyJson = true;

                    case "-d" -> {
                        if (mode != null) {
                            fail("Only one of <maven-log-file>, --dir, or --aggregate can be used.");
                        }
                        if (i + 1 >= args.length) {
                            fail("-d requires a directory path.");
                        }
                        mode = Mode.DIRECTORY;
                        directory = args[++i];
                    }

                    case "-a" -> {
                        if (mode != null) {
                            fail("Only one of <maven-log-file>, --dir, or --aggregate can be used.");
                        }
                        if (i + 1 >= args.length) {
                            fail("-a requires a glob pattern.");
                        }
                        mode = Mode.PATTERN;
                        aggregatePattern = args[++i];
                    }

                    default -> fail("Unknown option: " + arg);
                }
                continue;
            }

            // ---- positional argument: <maven-log-file> for SINGLE_LOG ----
            if (mode == null) {
                // first positional -> single-log mode
                if (logFile != null) {
                    fail("Too many positional arguments (only one <maven-log-file> is allowed).");
                }
                mode = Mode.SINGLE_LOG;
                logFile = arg;
            } else {
                // already in DIRECTORY / PATTERN mode, or already got a log file
                fail("Positional <maven-log-file> cannot be combined with --dir/--aggregate.");
            }
        }

        // Validate mode & required values
        if (mode == null) {
            fail("Missing input: provide either <maven-log-file>, --dir, or --aggregate.");
        }

        if (mode == Mode.SINGLE_LOG && logFile == null) {
            fail("Missing <maven-log-file> argument.");
        }

        if (mode == Mode.DIRECTORY && directory == null) {
            fail("Missing directory path for --dir/-d.");
        }

        if (mode == Mode.PATTERN && aggregatePattern == null) {
            fail("Missing glob pattern for --aggregate/-a.");
        }

        // pretty only makes sense with json
        if (prettyJson && !jsonOutput) {
            fail("--pretty / -p can only be used together with --json / -j.");
        }

        return new CliOptions(jsonOutput, prettyJson, mode, logFile, directory, aggregatePattern);
    }

    private static void fail(String message) {
        System.err.println("ERROR: " + message);
        printUsageAndExit();
    }

    public static void printUsageAndExit() {
        System.err.println("Usage: build-analyzer [options] <maven-log-file>");
        System.err.println("   or: build-analyzer [options] --dir <directory>");
        System.err.println("   or: build-analyzer [options] --aggregate <glob-pattern>");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  -j, --json           Output JSON instead of text");
        System.err.println("  -p, --pretty         Pretty-print JSON (requires -j/--json)");
        System.err.println("  -d, --dir <dir>      Analyze all log files under a directory");
        System.err.println("  -a, --aggregate <p>  Analyze all log files matching a glob pattern");
        System.exit(1);
    }
}
