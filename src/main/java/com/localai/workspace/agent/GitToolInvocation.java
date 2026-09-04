package com.localai.workspace.agent;

public record GitToolInvocation(
        String toolName,
        long durationMillis
) {
}
