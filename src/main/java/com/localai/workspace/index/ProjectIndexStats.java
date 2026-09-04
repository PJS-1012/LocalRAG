package com.localai.workspace.index;

import java.time.Instant;

public record ProjectIndexStats(
        String projectId,
        long storedChunkCount,
        String embeddingModel,
        int dimensions,
        Instant latestIndexedAt
) {
    public static ProjectIndexStats empty(String projectId) {
        return new ProjectIndexStats(projectId, 0, null, 0, null);
    }
}
