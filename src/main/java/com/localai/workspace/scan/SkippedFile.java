package com.localai.workspace.scan;

public record SkippedFile(
        String project,
        String file,
        long sizeBytes,
        String extension,
        String path,
        FileScanStatus status,
        String reason
) {
}
