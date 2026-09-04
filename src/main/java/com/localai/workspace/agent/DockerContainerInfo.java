package com.localai.workspace.agent;

public record DockerContainerInfo(
        String name,
        String image,
        String state,
        String status,
        String health
) {
}
