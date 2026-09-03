package com.localai.workspace.discovery;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;

public final class ProjectId {

    private ProjectId() {
    }

    public static String from(Path workspaceRoot, Path projectRoot) {
        Path normalizedWorkspace = workspaceRoot.toAbsolutePath().normalize();
        Path normalizedProject = projectRoot.toAbsolutePath().normalize();
        if (normalizedProject.equals(normalizedWorkspace) || !normalizedProject.startsWith(normalizedWorkspace)) {
            throw new IllegalArgumentException("Project root must be inside the Workspace");
        }
        return normalizedWorkspace.relativize(normalizedProject)
                .toString()
                .replace('\\', '/');
    }

    public static Optional<Path> resolve(Path workspaceRoot, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalizedSeparators = value.trim().replace('\\', '/');
        if (normalizedSeparators.startsWith("/")
                || Arrays.stream(normalizedSeparators.split("/", -1))
                .anyMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals(".."))) {
            return Optional.empty();
        }

        try {
            Path relativePath = Path.of(normalizedSeparators);
            if (relativePath.isAbsolute()) {
                return Optional.empty();
            }

            Path normalizedWorkspace = workspaceRoot.toAbsolutePath().normalize();
            Path candidate = normalizedWorkspace.resolve(relativePath).normalize();
            if (candidate.equals(normalizedWorkspace) || !candidate.startsWith(normalizedWorkspace)) {
                return Optional.empty();
            }
            return Optional.of(candidate);
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }
}
