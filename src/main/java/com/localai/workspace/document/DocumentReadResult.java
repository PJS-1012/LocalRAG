package com.localai.workspace.document;

public record DocumentReadResult(
        DocumentReadStatus status,
        WorkspaceDocument document,
        String reason
) {
    public static DocumentReadResult success(WorkspaceDocument document) {
        return new DocumentReadResult(DocumentReadStatus.READ_SUCCESS, document, null);
    }

    public static DocumentReadResult skipped(DocumentReadStatus status, String reason) {
        return new DocumentReadResult(status, null, reason);
    }
}
