package com.buildanalyzer.core.plugin;

public record PluginTiming(
        String pluginKey,           // e.g. "compiler:compile"
        int lineCount,        // 这个插件块里包含多少行日志
        double estimatedSeconds // 估算的耗时（基于模块总时间 * 行数占比）
) {}
