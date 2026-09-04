package com.localai.workspace.search;

import java.util.List;

public record ProjectSemanticSearchResponse(
        String projectId,
        String query,
        int topK,
        double threshold,
        boolean instructionEnabled,
        int queryEmbeddingDimension,
        int resultCount,
        long queryEmbeddingDurationMillis,
        long databaseSearchDurationMillis,
        long totalDurationMillis,
        ProjectSemanticSearchStatus status,
        String reason,
        List<ProjectSemanticSearchMatch> results
) {
}
