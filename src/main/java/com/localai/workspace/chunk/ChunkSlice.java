package com.localai.workspace.chunk;

public record ChunkSlice(
        String content,
        int startOffset,
        int endOffset,
        int startLine,
        int endLine
) {
}
