package com.localai.workspace.chunk;

import java.util.List;

public record ProjectChunkingResult(
        String projectName,
        String projectId,
        long documentCount,
        long chunkedDocumentCount,
        long emptyDocumentCount,
        long failedDocumentCount,
        long sourceReadFailedCount,
        long sourceSkippedCount,
        long chunkCount,
        double averageChunkSize,
        int minChunkSize,
        int maxChunkSize,
        long durationMillis,
        List<DocumentChunk> chunks,
        List<DocumentChunkingResult> documentResults
) {
}
