package com.localai.workspace.document;

public record ProjectFileReadResult(
        String relativePath,
        String absolutePath,
        DocumentReadStatus status,
        String reason
) {
}
