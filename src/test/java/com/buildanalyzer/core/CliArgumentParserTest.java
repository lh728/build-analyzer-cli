package com.buildanalyzer.core;

import com.buildanalyzer.cli.CliArgumentParser;
import com.buildanalyzer.cli.CliOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CliArgumentParserTest {

    @Test
    void parse_plainTextMode_withOnlyLogFile() {
        String[] args = {"sample-logs/build-parent.log"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertFalse(opts.jsonOutput());
        assertFalse(opts.prettyJson());
        assertEquals("sample-logs/build-parent.log", opts.logFile());
    }

    @Test
    void parse_jsonMode_withShortOptionJ() {
        String[] args = {"-j", "sample-logs/build-parent.log"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertTrue(opts.jsonOutput());
        assertFalse(opts.prettyJson());
        assertEquals("sample-logs/build-parent.log", opts.logFile());
    }

    @Test
    void parse_jsonPretty_withCombinedShortOptionsLowercase() {
        String[] args = {"-jp", "sample-logs/build-parent.log"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertTrue(opts.jsonOutput());
        assertTrue(opts.prettyJson());
        assertEquals("sample-logs/build-parent.log", opts.logFile());
    }

    @Test
    void parse_jsonPretty_withCombinedShortOptionsUppercase() {
        String[] args = {"-JP", "sample-logs/build-parent.log"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertTrue(opts.jsonOutput());
        assertTrue(opts.prettyJson());
        assertEquals("sample-logs/build-parent.log", opts.logFile());
    }

    @Test
    void parse_jsonPretty_withLongOptions() {
        String[] args = {"--json", "--pretty", "sample-logs/build-parent.log"};

        CliOptions opts = CliArgumentParser.parse(args);

        assertTrue(opts.jsonOutput());
        assertTrue(opts.prettyJson());
        assertEquals("sample-logs/build-parent.log", opts.logFile());
    }

}
