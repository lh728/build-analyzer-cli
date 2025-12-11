package com.buildanalyzer.core;

import com.buildanalyzer.cli.BuildAnalyzerCli;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildAnalyzerCliTest {

    @Test
    void main_shouldPrintHumanReadableSummary_inTextMode() {
        String[] args = {"sample-logs/build-parent.log"};

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

            BuildAnalyzerCli.main(args);

        } finally {
            System.setOut(originalOut);
        }

        String output = out.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("=== Build Analyzer CLI ==="));
        assertTrue(output.contains("Total build time"));
        assertTrue(output.contains("Modules total time"));
        assertTrue(output.contains("Other / overhead"));
        assertTrue(output.contains("Modules by time (share of whole build):"));
        assertTrue(output.contains("Slowest module: core"));
    }

    @Test
    void main_shouldPrintJsonSummary_whenJsonPrettyEnabled() {
        String[] args = {"-jp", "sample-logs/build-parent.log"};

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));

            BuildAnalyzerCli.main(args);

        } finally {
            System.setOut(originalOut);
        }

        String output = out.toString(StandardCharsets.UTF_8).trim();

        assertTrue(output.startsWith("{"));
        assertTrue(output.contains("\"totalSeconds\""));
        assertTrue(output.contains("\"modules\""));
        assertTrue(output.contains("\"name\""));
        assertTrue(output.contains("\"seconds\""));
    }
}
