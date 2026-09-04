package com.localai.workspace.embedding;

public enum ChunkEmbeddingStatus {
    EMBEDDED,
    EMPTY_CHUNK,
    EMBEDDING_FAILED,
    PROVIDER_FAILED,
    DIMENSION_MISMATCH,
    PROVIDER_UNAVAILABLE,
    MODEL_UNAVAILABLE
}
