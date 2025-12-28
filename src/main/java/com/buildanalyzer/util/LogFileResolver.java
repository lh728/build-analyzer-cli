package com.buildanalyzer.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper for discovering log files on disk.
 */
public final class LogFileResolver {

    /**
     * List all regular *.log files directly under the given directory.
     */
    public List<Path> listLogFilesInDirectory(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".log"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * List all regular files in the given directory matching the given glob pattern
     * (e.g. "build-*.log").
     */
    public List<Path> listLogFilesByPattern(Path dir, String filePattern) throws IOException {
        List<Path> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, filePattern)) {
            for (Path p : stream) {
                if (Files.isRegularFile(p)) {
                    result.add(p);
                }
            }
        }
        result.sort(null);
        return result;
    }
}

