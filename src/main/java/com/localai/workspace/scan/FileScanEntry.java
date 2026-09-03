package com.localai.workspace.scan;

public record FileScanEntry(
        String relativePath,
        String absolutePath,
        String fileName,
        String extension,
        long sizeBytes,
        FileScanStatus status,
        String reason
) {
}
