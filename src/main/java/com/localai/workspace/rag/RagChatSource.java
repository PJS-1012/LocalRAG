package com.localai.workspace.rag;

public record RagChatSource(
        String id,
        String filePath,
        String fileName,
        int startLine,
        int endLine
) {
}
