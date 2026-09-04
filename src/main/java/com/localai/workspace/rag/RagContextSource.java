package com.localai.workspace.rag;

public record RagContextSource(
        String citationId,
        int searchRank,
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
