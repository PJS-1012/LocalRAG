package com.localai.workspace.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "localrag.agent.logs")
public record LogInspectionProperties(
        int maxDepth,
        int maxFiles,
        int maxVisitedPaths,
        int defaultResults,
        int maxResults,
        int tailBytesPerFile,
        int maxTotalCharacters,
        int maxStackTraceLines,
        int maxQueryCharacters
) {
    public LogInspectionProperties {
        maxDepth = bounded(maxDepth, 4, 1, 8);
        maxFiles = bounded(maxFiles, 20, 1, 100);
        maxVisitedPaths = bounded(maxVisitedPaths, 5_000, maxFiles, 50_000);
        defaultResults = bounded(defaultResults, 20, 1, 100);
        maxResults = bounded(maxResults, 100, defaultResults, 200);
        tailBytesPerFile = bounded(tailBytesPerFile, 262_144, 4_096, 1_048_576);
        maxTotalCharacters = bounded(maxTotalCharacters, 16_000, 1_000, 65_536);
        maxStackTraceLines = bounded(maxStackTraceLines, 8, 0, 30);
        maxQueryCharacters = bounded(maxQueryCharacters, 200, 1, 500);
    }

    int appliedLimit(int requested) {
        int value = requested < 1 ? defaultResults : requested;
        return Math.min(value, maxResults);
    }

    private static int bounded(int value, int fallback, int minimum, int maximum) {
        int normalized = value < minimum ? fallback : value;
        return Math.min(normalized, maximum);
    }
}
