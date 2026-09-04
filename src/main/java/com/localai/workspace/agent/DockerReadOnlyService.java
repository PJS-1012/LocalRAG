package com.localai.workspace.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localai.workspace.discovery.DetectedProject;
import com.localai.workspace.discovery.ProjectDiscoveryService;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DockerReadOnlyService {

    private static final List<String> COMPOSE_FILE_NAMES = List.of(
            "compose.yml", "compose.yaml", "docker-compose.yml", "docker-compose.yaml"
    );
    private static final Pattern HEALTH_PATTERN = Pattern.compile(
            "\\((healthy|unhealthy|health: starting)\\)", Pattern.CASE_INSENSITIVE
    );

    private final DockerProcessRunner processRunner;
    private final ProjectDiscoveryService projectDiscoveryService;
    private final ObjectMapper objectMapper;

    public DockerReadOnlyService(
            DockerProcessRunner processRunner,
            ProjectDiscoveryService projectDiscoveryService,
            ObjectMapper objectMapper
    ) {
        this.processRunner = processRunner;
        this.projectDiscoveryService = projectDiscoveryService;
        this.objectMapper = objectMapper;
    }

    public DockerStatusResult getStatus() {
        DockerCommandResult command = processRunner.engineVersion();
        if (!command.successful()) {
            LocalEnvironmentStatus status = classifyFailure(command);
            return new DockerStatusResult(status, false, false, null, failureReason(status, command));
        }
        return new DockerStatusResult(
                LocalEnvironmentStatus.AVAILABLE, true, true, command.output().strip(), null
        );
    }

    public DockerContainersResult getContainers() {
        DockerCommandResult command = processRunner.runningContainers();
        if (!command.successful()) {
            LocalEnvironmentStatus status = classifyFailure(command);
            return new DockerContainersResult(status, 0, List.of(), failureReason(status, command));
        }
        try {
            List<DockerContainerInfo> containers = parseContainers(command.output());
            return new DockerContainersResult(
                    LocalEnvironmentStatus.AVAILABLE, containers.size(), containers, null
            );
        } catch (JsonProcessingException exception) {
            return new DockerContainersResult(
                    LocalEnvironmentStatus.FAILED, 0, List.of(), "Docker returned an unreadable container summary"
            );
        }
    }

    public ProjectContainerStatusResult getProjectContainers(String projectId) {
        Optional<DetectedProject> project = resolve(projectId);
        if (project.isEmpty()) {
            return projectFailure(projectId, LocalEnvironmentStatus.PROJECT_NOT_FOUND,
                    "Project ID was not found in the Workspace");
        }

        Optional<Path> composeFile = findComposeFile(project.get().rootPath());
        if (composeFile.isEmpty()) {
            return projectFailure(projectId, LocalEnvironmentStatus.NOT_CONFIGURED,
                    "No Compose file exists at the resolved Project root");
        }

        DockerCommandResult command = processRunner.projectContainers(project.get().rootPath(), composeFile.get());
        if (!command.successful()) {
            LocalEnvironmentStatus status = classifyFailure(command);
            return new ProjectContainerStatusResult(
                    projectId, status, composeFile.get().getFileName().toString(), 0, List.of(),
                    failureReason(status, command)
            );
        }

        try {
            List<DockerContainerInfo> containers = parseContainers(command.output());
            return new ProjectContainerStatusResult(
                    projectId, LocalEnvironmentStatus.AVAILABLE,
                    composeFile.get().getFileName().toString(), containers.size(), containers, null
            );
        } catch (JsonProcessingException exception) {
            return new ProjectContainerStatusResult(
                    projectId, LocalEnvironmentStatus.FAILED,
                    composeFile.get().getFileName().toString(), 0, List.of(),
                    "Docker Compose returned an unreadable container summary"
            );
        }
    }

    private List<DockerContainerInfo> parseContainers(String output) throws JsonProcessingException {
        String normalized = output.strip();
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<JsonNode> nodes = new ArrayList<>();
        if (normalized.startsWith("[")) {
            JsonNode array = objectMapper.readTree(normalized);
            array.forEach(nodes::add);
        } else {
            for (String line : normalized.lines().toList()) {
                if (!line.isBlank()) {
                    nodes.add(objectMapper.readTree(line));
                }
            }
        }

        return nodes.stream().map(this::toContainer).toList();
    }

    private DockerContainerInfo toContainer(JsonNode node) {
        String name = firstText(node, "Name", "Names");
        String image = firstText(node, "Image");
        String state = firstText(node, "State");
        String status = firstText(node, "Status");
        String health = firstText(node, "Health");
        if (health == null && status != null) {
            Matcher matcher = HEALTH_PATTERN.matcher(status);
            if (matcher.find()) {
                health = matcher.group(1).toUpperCase(Locale.ROOT).replace(' ', '_').replace(':', '_');
            }
        }
        return new DockerContainerInfo(name, image, state, status, health);
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private Optional<Path> findComposeFile(Path projectRoot) {
        return COMPOSE_FILE_NAMES.stream()
                .map(projectRoot::resolve)
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .findFirst();
    }

    private Optional<DetectedProject> resolve(String projectId) {
        try {
            return projectDiscoveryService.findProject(projectDiscoveryService.defaultWorkspaceRoot(), projectId);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private LocalEnvironmentStatus classifyFailure(DockerCommandResult command) {
        if (!command.started()) {
            return LocalEnvironmentStatus.NOT_INSTALLED;
        }
        String output = command.output().toLowerCase(Locale.ROOT);
        if (output.contains("access is denied") || output.contains("permission denied")
                || output.contains("forbidden")) {
            return LocalEnvironmentStatus.ACCESS_DENIED;
        }
        if (command.timedOut() || command.truncated()) {
            return LocalEnvironmentStatus.FAILED;
        }
        return LocalEnvironmentStatus.NOT_RUNNING;
    }

    private String failureReason(LocalEnvironmentStatus status, DockerCommandResult command) {
        return switch (status) {
            case NOT_INSTALLED -> "Docker CLI is not installed or not available";
            case ACCESS_DENIED -> "Docker Engine access was denied";
            case NOT_RUNNING -> "Docker Engine is not currently reachable";
            case FAILED -> command.timedOut()
                    ? "Docker read operation timed out"
                    : "Docker read result exceeded the safe limit or could not be read";
            default -> "Docker status could not be determined";
        };
    }

    private ProjectContainerStatusResult projectFailure(
            String projectId, LocalEnvironmentStatus status, String reason
    ) {
        return new ProjectContainerStatusResult(projectId, status, null, 0, List.of(), reason);
    }
}
