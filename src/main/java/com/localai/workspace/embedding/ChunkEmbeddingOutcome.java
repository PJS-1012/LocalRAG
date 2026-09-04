package com.localai.workspace.embedding;

import com.localai.workspace.chunk.DocumentChunk;

public record ChunkEmbeddingOutcome(
        DocumentChunk chunk,
        ChunkEmbeddingStatus status,
        EmbeddedChunk embeddedChunk,
        String reason
) {
    public static ChunkEmbeddingOutcome success(EmbeddedChunk embeddedChunk) {
        return new ChunkEmbeddingOutcome(
                embeddedChunk.chunk(),
                ChunkEmbeddingStatus.EMBEDDED,
                embeddedChunk,
                null
        );
    }

    public static ChunkEmbeddingOutcome failed(
            DocumentChunk chunk,
            ChunkEmbeddingStatus status,
            String reason
    ) {
        return new ChunkEmbeddingOutcome(chunk, status, null, reason);
    }
}
