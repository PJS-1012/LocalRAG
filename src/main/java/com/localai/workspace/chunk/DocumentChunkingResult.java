package com.localai.workspace.chunk;

public record DocumentChunkingResult(
        String sourceFilePath,
        ChunkingStatus status,
        int chunkCount,
        String reason
) {
}
