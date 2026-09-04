package com.localai.workspace.agent;

public record AgentToolInvocation(
        String toolName,
        long durationMillis
) {
}
