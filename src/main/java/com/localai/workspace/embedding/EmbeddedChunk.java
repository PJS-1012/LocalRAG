package com.localai.workspace.embedding;

import com.localai.workspace.chunk.DocumentChunk;

public record EmbeddedChunk(
        DocumentChunk chunk,
        String embeddingModel,
        int dimensions,
        float[] vector
) {
}
