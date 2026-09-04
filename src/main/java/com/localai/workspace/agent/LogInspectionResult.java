package com.localai.workspace.agent;

import java.util.List;

public record LogInspectionResult(
        String projectId,
        LogToolStatus status,
        int discoveredFiles,
        int scannedFiles,
        int failedFiles,
        int resultCount,
        boolean truncated,
        int totalCharacters,
        long discoveryDurationMillis,
        long readDurationMillis,
        List<LogEntry> entries,
        String reason
) {
}
