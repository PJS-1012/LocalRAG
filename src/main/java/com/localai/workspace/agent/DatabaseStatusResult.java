package com.localai.workspace.agent;

public record DatabaseStatusResult(
        LocalEnvironmentStatus status,
        boolean reachable,
        boolean pgvectorAvailable,
        String reason
) {
}
