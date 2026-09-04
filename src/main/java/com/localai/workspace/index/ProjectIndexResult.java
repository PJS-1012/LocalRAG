package com.localai.workspace.index;

public record ProjectIndexResult(
        String projectId,
        long documentCount,
        long chunkCount,
        long embeddedCount,
        long writtenCount,
        long storedCount,
        long deletedCount,
        long failedCount,
        int dimensions,
        String embeddingModel,
        long chunkingDurationMillis,
        long embeddingDurationMillis,
        long databaseWriteDurationMillis,
        long totalDurationMillis,
        ProjectIndexStatus status,
        String reason
) {
}
