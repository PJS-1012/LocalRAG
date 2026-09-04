package com.localai.workspace.agent;

public record DockerStatusResult(
        LocalEnvironmentStatus status,
        boolean engineAccessible,
        boolean running,
        String engineVersion,
        String reason
) {
}
