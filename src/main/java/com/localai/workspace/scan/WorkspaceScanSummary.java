package com.localai.workspace.scan;

import java.util.List;

public record WorkspaceScanSummary(
        String workspaceRoot,
        long totalProjects,
        long successfulProjects,
        long failedProjects,
        long totalFiles,
        long includedFiles,
        long excludedFiles,
        long failedFiles,
        long skippedTooLargeFiles,
        long durationMillis,
        List<WorkspaceProjectScanResult> projects
) {
}
