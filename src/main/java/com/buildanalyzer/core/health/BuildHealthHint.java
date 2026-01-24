package com.buildanalyzer.core.health;

public record BuildHealthHint(
        HealthSeverity severity,
        String scope,
        String message
) {}
