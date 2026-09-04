package com.localai.workspace.search;

public record ProjectSemanticSearchRequest(
        String projectId,
        String query,
        Integer topK,
        Double threshold
) {
}
