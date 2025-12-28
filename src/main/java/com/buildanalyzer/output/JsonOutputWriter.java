package com.buildanalyzer.output;

import com.buildanalyzer.core.aggregate.AggregatedSummary;
import com.buildanalyzer.core.model.BuildSummary;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Path;
import java.util.List;

/**
 * Centralizes all JSON output.
 */
public class JsonOutputWriter {

    private final Gson compactGson = new Gson();
    private final Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();

    public void printSingleBuild(BuildSummary summary, boolean pretty) {
        Gson gson = pretty ? prettyGson : compactGson;
        System.out.println(gson.toJson(summary));
    }

    public void printAggregated(String modeLabel,
                                List<Path> logFiles,
                                AggregatedSummary summary,
                                boolean pretty) {

        List<String> fileStrings = logFiles.stream()
                .map(Path::toString)
                .toList();

        AggregatedJsonResult dto = new AggregatedJsonResult(modeLabel, fileStrings, summary);

        Gson gson = pretty ? prettyGson : compactGson;
        System.out.println(gson.toJson(dto));
    }

    private record AggregatedJsonResult(
            String mode,
            List<String> logFiles,
            AggregatedSummary summary
    ) {}
}

