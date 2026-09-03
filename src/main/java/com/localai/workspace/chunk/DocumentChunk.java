package com.localai.workspace.chunk;

import java.time.Instant;

public record DocumentChunk(
        String chunkId,
        String projectId,
        String sourceFilePath,
        String sourceFileName,
        String extension,
        int chunkIndex,
        String content,
        int startOffset,
        int endOffset,
        int startLine,
        int endLine,
        String sourceFingerprint,
        Instant sourceModifiedAt
) {
}
