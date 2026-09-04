package com.localai.workspace.agent;

import java.util.List;

public record ProjectContainerStatusResult(
        String projectId,
        LocalEnvironmentStatus status,
        String composeFile,
        int containerCount,
        List<DockerContainerInfo> containers,
        String reason
) {
}
