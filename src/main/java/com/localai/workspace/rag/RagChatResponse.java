package com.localai.workspace.rag;

import java.util.List;

public record RagChatResponse(
        String projectId,
        String query,
        String answer,
        int sourceCount,
        List<RagChatSource> sources,
        int contextCharacters,
        long retrievalContextDurationMillis,
        long llmDurationMillis,
        long totalDurationMillis,
        RagChatStatus status,
        List<String> usedSourceIds,
        List<String> invalidSourceIds,
        List<String> warnings
) {
}
