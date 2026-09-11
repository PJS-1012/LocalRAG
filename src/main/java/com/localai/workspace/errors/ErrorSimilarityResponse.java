package com.localai.workspace.errors;

import java.util.List;

public record ErrorSimilarityResponse(
        String status, String projectId, int topK, double threshold, int resultCount,
        long embeddingDurationMillis, long vectorSearchDurationMillis, long totalDurationMillis,
        String caution, List<ErrorSimilarResult> results
) { }
