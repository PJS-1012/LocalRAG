package com.localai.workspace.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;

final class DockerAgentTools implements AgentToolTracker {

    private final String allowedProjectId;
    private final DockerReadOnlyService dockerService;
    private final List<AgentToolInvocation> invocations = new ArrayList<>();
    private boolean failed;

    DockerAgentTools(String allowedProjectId, DockerReadOnlyService dockerService) {
        this.allowedProjectId = allowedProjectId;
        this.dockerService = dockerService;
    }

    @Tool(
            name = "getDockerStatus",
            description = "Read whether the local Docker Engine is currently reachable and running. "
                    + "Use for direct questions about Docker being installed, alive, available, or running."
    )
    DockerStatusResult getDockerStatus() {
        long startedAt = System.nanoTime();
        DockerStatusResult result = dockerService.getStatus();
        record("getDockerStatus", startedAt, result.status());
        return result;
    }

    @Tool(
            name = "getDockerContainers",
            description = "Read a bounded structured list of currently running Docker containers, including name, "
                    + "image, state, status, and health when available. Never returns raw Docker output."
    )
    DockerContainersResult getDockerContainers() {
        long startedAt = System.nanoTime();
        DockerContainersResult result = dockerService.getContainers();
        record("getDockerContainers", startedAt, result.status());
        return result;
    }

    @Tool(
            name = "getProjectContainerStatus",
            description = "Read Docker Compose container status only when the exact requested Project root contains "
                    + "an explicit Compose file. Never infer Project relationships from similar container names."
    )
    ProjectContainerStatusResult getProjectContainerStatus(
            @ToolParam(description = "Exact projectId from the current user request") String projectId
    ) {
        long startedAt = System.nanoTime();
        ProjectContainerStatusResult result = allowedProjectId.equals(projectId)
                ? dockerService.getProjectContainers(projectId)
                : new ProjectContainerStatusResult(
                projectId, LocalEnvironmentStatus.PROJECT_SCOPE_MISMATCH, null, 0, List.of(),
                "Tool projectId does not match the requested project"
        );
        record("getProjectContainerStatus", startedAt, result.status());
        return result;
    }

    @Override
    public List<AgentToolInvocation> invocations() {
        return List.copyOf(invocations);
    }

    @Override
    public boolean failed() {
        return failed;
    }

    private void record(String toolName, long startedAt, LocalEnvironmentStatus status) {
        invocations.add(new AgentToolInvocation(toolName, (System.nanoTime() - startedAt) / 1_000_000));
        if (status != LocalEnvironmentStatus.AVAILABLE) {
            failed = true;
        }
    }
}
