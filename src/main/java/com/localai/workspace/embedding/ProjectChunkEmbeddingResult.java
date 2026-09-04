package com.localai.workspace.embedding;

import java.util.List;

public record ProjectChunkEmbeddingResult(
        String projectId,
        long documentCount,
        long chunkCount,
        long embeddedCount,
        long failedCount,
        long sourceReadFailedCount,
        long chunkingFailedDocumentCount,
        String embeddingModel,
        int dimensions,
        EmbeddingRunStatus status,
        String providerFailureReason,
        long chunkingDurationMillis,
        long embeddingDurationMillis,
        double averageEmbeddingMillisPerChunk,
        long totalDurationMillis,
        List<ChunkEmbeddingOutcome> outcomes
) {
}
