package com.localai.workspace.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectDiscoveryServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void discoversOnlyDirectChildDirectories() throws IOException {
        Path directProject = Files.createDirectory(tempDirectory.resolve("direct-project"));
        Files.createDirectory(directProject.resolve(".git"));
        Files.createDirectories(directProject.resolve("nested-project/.git"));
        Files.writeString(tempDirectory.resolve("ordinary-file.txt"), "not a project directory");

        ProjectDiscoveryService service = new ProjectDiscoveryService(
                new WorkspaceProperties(tempDirectory),
                new ProjectTypeDetector()
        );

        List<DetectedProject> projects = service.discoverProjects();

        assertThat(projects)
                .extracting(DetectedProject::name)
                .containsExactly("direct-project");
    }
}
