package com.localai.workspace.errors;

public record ErrorEmbeddingIndexResult(
        Status status, int dimensions, long embeddingDurationMillis, long databaseDurationMillis, String reason
) {
    public enum Status { INDEXED, UNCHANGED, EMBEDDING_FAILED }
}
