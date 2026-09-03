package com.localai.workspace.discovery;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
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
        return discoverWorkspace(workspacePath).projects();
    }

    public WorkspaceDiscoveryResult discoverWorkspace() {
        return discoverWorkspace(defaultWorkspaceRoot());
    }

    public WorkspaceDiscoveryResult discoverWorkspace(Path workspacePath) {
        Path workspaceRoot = workspacePath.toAbsolutePath().normalize();
        if (!Files.isDirectory(workspaceRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new WorkspaceAccessException("Workspace root is not an accessible directory: " + workspaceRoot);
        }

        List<DetectedProject> projects = new ArrayList<>();
        List<DetectedContainer> containers = new ArrayList<>();
        for (Path folder : childDirectories(workspaceRoot)) {
            DetectedProject directCandidate = detector.detect(folder);
            if (directCandidate.projectType() != ProjectType.UNKNOWN) {
                projects.add(directCandidate);
                continue;
            }

            List<DetectedProject> nestedProjects = childDirectories(folder).stream()
                    .map(detector::detect)
                    .filter(candidate -> candidate.projectType() != ProjectType.UNKNOWN)
                    .toList();
            if (nestedProjects.isEmpty()) {
                projects.add(directCandidate);
            } else {
                containers.add(new DetectedContainer(
                        directCandidate.name(),
                        directCandidate.rootPath(),
                        directCandidate.gitRepository(),
                        nestedProjects
                ));
                projects.addAll(nestedProjects);
            }
        }

        return new WorkspaceDiscoveryResult(
                workspaceRoot,
                List.copyOf(projects),
                List.copyOf(containers)
        );
    }

    private List<Path> childDirectories(Path root) {
        try (Stream<Path> children = Files.list(root)) {
            return children
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .toList();
        } catch (IOException exception) {
            throw new WorkspaceAccessException("Failed to read directory during Project discovery: " + root, exception);
        }
    }

    public Optional<DetectedProject> findProject(Path workspaceRoot, String projectName) {
        return discoverProjects(workspaceRoot).stream()
                .filter(candidate -> candidate.name().equalsIgnoreCase(projectName))
                .findFirst();
    }
}
