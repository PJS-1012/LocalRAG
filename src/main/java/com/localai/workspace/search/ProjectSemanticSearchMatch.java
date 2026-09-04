package com.localai.workspace.search;

public record ProjectSemanticSearchMatch(
        int rank,
        String chunkId,
        String projectId,
        String filePath,
        String fileName,
        String extension,
        int chunkIndex,
        int startLine,
        int endLine,
        String content,
        double similarity,
        String embeddingModel
) {
}
