package com.buildanalyzer.cli;

import java.util.ArrayList;
import java.util.List;

public final class CliArgumentParser {

    private CliArgumentParser() {
    }

    public static CliOptions parse(String[] args) {
        if (args == null || args.length == 0) {
            printUsageAndExit();
        }

        boolean json = false;
        boolean pretty = false;
        Mode mode = null;

        String logFile = null;
        String dir = null;
        String pattern = null;
        String projectDir = null;
        List<String> extraMavenArgs = new ArrayList<>();

        boolean afterDoubleDash = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (afterDoubleDash) {
                // 全部当作 Maven 参数转发
                extraMavenArgs.add(arg);
                continue;
            }

            switch (arg) {
                case "--" -> {
                    // 语义：后面的参数原封不动传给 Maven
                    afterDoubleDash = true;
                }
                case "-j", "--json" -> json = true;
                case "-p", "--pretty" -> pretty = true;

                case "-d", "--dir" -> {
                    ensureModeUnsetOrSame(mode, Mode.DIRECTORY);
                    mode = Mode.DIRECTORY;

                    if (i + 1 >= args.length) {
                        System.err.println("ERROR: --dir/-d requires a directory path.");
                        printUsageAndExit();
                    }
                    dir = args[++i];
                }

                case "-a", "--aggregate" -> {
                    ensureModeUnsetOrSame(mode, Mode.PATTERN);
                    mode = Mode.PATTERN;

                    if (i + 1 >= args.length) {
                        System.err.println("ERROR: --aggregate/-a requires a glob pattern.");
                        printUsageAndExit();
                    }
                    pattern = args[++i];
                }

                case "-C", "--clean-install" -> {
                    ensureModeUnsetOrSame(mode, Mode.CLEAN_INSTALL);
                    mode = Mode.CLEAN_INSTALL;
                }

                default -> {
                    if (arg.startsWith("-")) {
                        System.err.println("Unknown option: " + arg);
                        printUsageAndExit();
                    }

                    // 当前位置是“无前缀”的位置参数
                    if (mode == Mode.DIRECTORY || mode == Mode.PATTERN) {
                        System.err.println("Too many positional arguments.");
                        printUsageAndExit();
                    } else if (mode == Mode.CLEAN_INSTALL) {
                        // 作为 <project-dir>
                        if (projectDir != null) {
                            System.err.println(
                                    "Too many positional arguments for --clean-install (at most one <project-dir>).");
                            printUsageAndExit();
                        }
                        projectDir = arg;
                    } else {
                        // 默认：单日志模式
                        if (logFile != null) {
                            System.err.println(
                                    "Too many positional arguments (only one <maven-log-file> is allowed).");
                            printUsageAndExit();
                        }
                        mode = Mode.SINGLE_LOG;
                        logFile = arg;
                    }
                }
            }
        }

        if (pretty && !json) {
            System.err.println("--pretty / -p can only be used together with --json / -j.");
            printUsageAndExit();
        }

        if (mode == null) {
            System.err.println(
                    "Missing mode: provide either <maven-log-file>, --dir, --aggregate or --clean-install.");
            printUsageAndExit();
        }

        // 模式特定的必填参数检查
        switch (mode) {
            case SINGLE_LOG -> {
                if (logFile == null) {
                    System.err.println("Missing <maven-log-file> argument.");
                    printUsageAndExit();
                }
            }
            case DIRECTORY -> {
                if (dir == null) {
                    System.err.println("Missing directory path for --dir/-d.");
                    printUsageAndExit();
                }
            }
            case PATTERN -> {
                if (pattern == null) {
                    System.err.println("Missing glob pattern for --aggregate/-a.");
                    printUsageAndExit();
                }
            }
            case CLEAN_INSTALL -> {
                // projectDir 可以为空 -> 默认"."
            }
        }

        return new CliOptions(
                mode,
                json,
                pretty,
                logFile,
                dir,
                pattern,
                projectDir,
                List.copyOf(extraMavenArgs)
        );
    }

    private static void ensureModeUnsetOrSame(Mode current, Mode newMode) {
        if (current != null && current != newMode) {
            System.err.println("Cannot combine mode " + newMode + " with " + current + ".");
            printUsageAndExit();
        }
    }

    private static void printUsageAndExit() {
        System.err.println("Usage:");
        System.err.println("  build-analyzer [options] <maven-log-file>");
        System.err.println("  build-analyzer --dir <log-directory>");
        System.err.println("  build-analyzer --aggregate <glob-pattern>");
        System.err.println("  build-analyzer --clean-install [<project-dir>] [-- <maven-args...>]");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  -j, --json                 Output JSON instead of text");
        System.err.println("  -p, --pretty               Pretty-print JSON (requires -j/--json)");
        System.err.println("  -d, --dir <dir>            Aggregate all *.log files directly under <dir>");
        System.err.println("  -a, --aggregate <pattern>  Aggregate log files matching glob pattern");
        System.err.println("                             (e.g. ci-logs/build-*.log)");
        System.err.println("  -C, --clean-install        Run 'mvn clean install' in the given project directory");
        System.err.println("                             (default: current directory).");
        System.err.println("                             Use '--' to pass additional arguments to Maven.");
        System.exit(1);
    }
}
