package com.buildanalyzer.core.plugin;

import com.buildanalyzer.core.model.BuildSummary;
import com.buildanalyzer.core.model.ModuleSummary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从原始 Maven 日志中按插件块进行切分，
 * 再根据「模块总耗时 × 日志行数占比」粗略估算每个插件的时间。
 *
 * 注意：这只是 heuristic（启发式），并不是 Maven 精确的 plugin timing。
 */
public class PluginTimingAnalyzer {

    // 例子：
    // [INFO] --- compiler:3.13.0:compile (default-compile) @ core ---
    // group1 = compiler
    // group2 = 3.13.0
    // group3 = compile
    // group4 = core
    private static final Pattern PLUGIN_HEADER =
            Pattern.compile("\\[INFO] ---\\s+([^: ]+):([^:]+):([^\\s(]+).* @ (.+?) ---");

    /**
     * 入口 1：给 logPath + BuildSummary，内部自己读文件。
     */
    public Map<String, List<PluginTiming>> analyze(Path logPath,
                                                   BuildSummary summary) throws IOException {

        List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);

        Map<String, Double> moduleSeconds = new HashMap<>();
        for (ModuleSummary m : summary.getModules()) {
            moduleSeconds.put(m.getName(), m.getSeconds());
        }

        return analyze(lines, moduleSeconds);
    }

    /**
     * 入口 2：给行列表 + module 总时间 map，方便以后复用/测试。
     */
    public Map<String, List<PluginTiming>> analyze(List<String> lines,
                                                   Map<String, Double> moduleSeconds) {

        // module -> (pluginKey -> lineCount)
        Map<String, Map<String, Integer>> perModulePluginLines = new LinkedHashMap<>();
        // module -> total plugin lines
        Map<String, Integer> perModuleTotalLines = new LinkedHashMap<>();

        String currentModule = null;
        String currentPluginKey = null;
        int currentBlockStart = -1; // header 的行号

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher matcher = PLUGIN_HEADER.matcher(line);
            if (matcher.find()) {
                // 收尾上一个 plugin block
                if (currentModule != null && currentPluginKey != null && currentBlockStart >= 0) {
                    int lineCount = i - (currentBlockStart + 1); // 不含 header
                    if (lineCount > 0) {
                        accumulate(perModulePluginLines, perModuleTotalLines,
                                currentModule, currentPluginKey, lineCount);
                    }
                }

                String plugin = matcher.group(1); // compiler / clean / surefire ...
                String goal = matcher.group(3);   // compile / test / clean ...
                String module = matcher.group(4).trim();

                currentModule = module;
                currentPluginKey = plugin + ":" + goal; // e.g. compiler:compile
                currentBlockStart = i;
            }
        }

        // 文件末尾再收尾一次
        if (currentModule != null && currentPluginKey != null && currentBlockStart >= 0) {
            int lineCount = lines.size() - (currentBlockStart + 1);
            if (lineCount > 0) {
                accumulate(perModulePluginLines, perModuleTotalLines,
                        currentModule, currentPluginKey, lineCount);
            }
        }

        // 基于行数占比估算时间
        Map<String, List<PluginTiming>> result = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Integer>> moduleEntry : perModulePluginLines.entrySet()) {
            String module = moduleEntry.getKey();
            Map<String, Integer> pluginLines = moduleEntry.getValue();
            int totalLines = perModuleTotalLines.getOrDefault(module, 0);
            double moduleSec = moduleSeconds.getOrDefault(module, 0.0);

            List<PluginTiming> timings = new ArrayList<>();

            for (Map.Entry<String, Integer> pluginEntry : pluginLines.entrySet()) {
                String pluginKey = pluginEntry.getKey();
                int linesForPlugin = pluginEntry.getValue();

                double estimated = 0.0;
                if (moduleSec > 0.0 && totalLines > 0 && linesForPlugin > 0) {
                    estimated = moduleSec * ((double) linesForPlugin / (double) totalLines);
                }

                timings.add(new PluginTiming(pluginKey, linesForPlugin, estimated));
            }

            // 按估算时间从大到小排一下
            timings.sort(Comparator.comparingDouble(PluginTiming::estimatedSeconds).reversed());
            result.put(module, timings);
        }

        return result;
    }

    // 累加某个 module 里某个 plugin 的行数
    private static void accumulate(Map<String, Map<String, Integer>> perModulePluginLines,
                                   Map<String, Integer> perModuleTotalLines,
                                   String module,
                                   String pluginKey,
                                   int lineCount) {

        perModulePluginLines
                .computeIfAbsent(module, m -> new LinkedHashMap<>())
                .merge(pluginKey, lineCount, Integer::sum);

        perModuleTotalLines.merge(module, lineCount, Integer::sum);
    }
}

