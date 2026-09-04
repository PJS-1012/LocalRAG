package com.localai.workspace.embedding;

public enum EmbeddingRunStatus {
    SUCCESS,
    PARTIAL_FAILURE,
    FAILED,
    PROVIDER_FAILED,
    PROVIDER_UNAVAILABLE,
    MODEL_UNAVAILABLE
}
