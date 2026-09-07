package com.localai.workspace.agent;

/** Request-local execution order. Arguments are deliberately not exposed. */
public record AgentToolCall(
        int sequence, String toolName, long durationMillis, String outcome,
        boolean successful, Integer sameArgumentsAs
) {
}
