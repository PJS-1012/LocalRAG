package com.localai.workspace.agent;

public record LogEntry(
        String timestamp,
        String level,
        String message,
        String sourceFile,
        Long lineNumber
) {
}
