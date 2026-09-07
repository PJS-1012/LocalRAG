package com.localai.workspace.agent;

import java.util.List;

public record AgentChatResponse(
        String projectId,
        String query,
        String answer,
        List<String> toolsUsed,
        long toolExecutionDurationMillis,
        long llmDurationMillis,
        long totalDurationMillis,
        AgentChatStatus status,
        List<String> warnings,
        List<AgentToolCall> toolCalls
) {
}
