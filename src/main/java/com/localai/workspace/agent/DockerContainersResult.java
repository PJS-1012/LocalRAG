package com.localai.workspace.agent;

import java.util.List;

public record DockerContainersResult(
        LocalEnvironmentStatus status,
        int containerCount,
        List<DockerContainerInfo> containers,
        String reason
) {
}
