package com.localai.workspace.document;

import java.util.List;

public record ProjectDocumentReadResult(
        String projectName,
        String projectId,
        long totalFiles,
        long successCount,
        long failedCount,
        long skippedCount,
        long totalTextBytes,
        long durationMillis,
        List<WorkspaceDocument> documents,
        List<ProjectFileReadResult> fileResults
) {
}
