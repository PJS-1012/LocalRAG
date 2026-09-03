package com.localai.workspace.scan;

import com.localai.workspace.discovery.ProjectType;

import java.util.List;

public record WorkspaceProjectScanResult(
        String projectName,
        ProjectType projectType,
        String detectedFramework,
        boolean gitRepository,
        List<String> detectionHints,
        WorkspaceProjectScanStatus status,
        String failureReason,
        long totalFiles,
        long includedFiles,
        long excludedFiles,
        long failedFiles,
        long skippedTooLargeFiles,
        List<SkippedFile> skippedTooLargeFileDetails,
        long durationMillis
) {
}
