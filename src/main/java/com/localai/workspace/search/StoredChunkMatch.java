package com.localai.workspace.search;

record StoredChunkMatch(
        String chunkId,
        String projectId,
        String sourcePath,
        String sourceFileName,
        String extension,
        int chunkIndex,
        int startLine,
        int endLine,
        String content,
        double similarity,
        String embeddingModel
) {
}
