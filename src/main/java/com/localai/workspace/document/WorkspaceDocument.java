package com.localai.workspace.document;

import java.time.Instant;

public record WorkspaceDocument(
        String projectName,
        String projectId,
        String fileName,
        String filePath,
        String relativePath,
        String extension,
        long size,
        Instant modifiedAt,
        String content
) {
}
