package com.localai.workspace.scan;

import com.localai.workspace.discovery.ProjectType;

import java.util.List;
import java.util.Map;

public record ProjectScanResult(
        String project,
        String projectId,
        String rootPath,
        ProjectType projectType,
        long totalDetectedFiles,
        long supportedFiles,
        long excludedFiles,
        long failedFiles,
        long skippedTooLargeFiles,
        Map<FileScanStatus, Long> statusCounts,
        List<String> excludedDirectories,
        List<SkippedFile> tooLargeFiles
) {
}
