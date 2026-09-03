package com.localai.workspace.discovery;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class ProjectDiscoveryService {

    private final WorkspaceProperties properties;
    private final ProjectTypeDetector detector;

    public ProjectDiscoveryService(WorkspaceProperties properties, ProjectTypeDetector detector) {
        this.properties = properties;
        this.detector = detector;
    }

    public List<DetectedProject> discoverProjects() {
        return discoverProjects(defaultWorkspaceRoot());
    }

    public Path defaultWorkspaceRoot() {
        return properties.rootPath().toAbsolutePath().normalize();
    }

    public List<DetectedProject> discoverProjects(Path workspacePath) {
        Path workspaceRoot = workspacePath.toAbsolutePath().normalize();
        if (!Files.isDirectory(workspaceRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new WorkspaceAccessException("Workspace root is not an accessible directory: " + workspaceRoot);
        }

        try (Stream<Path> children = Files.list(workspaceRoot)) {
            return children
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .map(detector::detect)
                    .toList();
        } catch (IOException exception) {
            throw new WorkspaceAccessException("Failed to read workspace root: " + workspaceRoot, exception);
        }
    }

    public Optional<DetectedProject> findProject(Path workspaceRoot, String projectName) {
        return discoverProjects(workspaceRoot).stream()
                .filter(candidate -> candidate.name().equalsIgnoreCase(projectName))
                .findFirst();
    }
}
