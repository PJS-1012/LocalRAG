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

    @Test
    void discoversFromSuppliedWorkspaceInsteadOfConfiguredDefault() throws IOException {
        Path configuredDefault = Files.createDirectory(tempDirectory.resolve("configured-default"));
        Path suppliedWorkspace = Files.createDirectory(tempDirectory.resolve("supplied-workspace"));
        Files.createDirectory(suppliedWorkspace.resolve("dynamic-project"));
        ProjectDiscoveryService service = new ProjectDiscoveryService(
                new WorkspaceProperties(configuredDefault),
                new ProjectTypeDetector()
        );

        List<DetectedProject> projects = service.discoverProjects(suppliedWorkspace);

        assertThat(projects)
                .extracting(DetectedProject::name)
                .containsExactly("dynamic-project");
    }
    @Test
    void discoversOneLevelNestedProjectsAndKeepsContainerSeparate() throws IOException {
        Path container = Files.createDirectory(tempDirectory.resolve("container"));
        Files.createDirectory(container.resolve(".git"));

        Path backend = Files.createDirectory(container.resolve("backend"));
        Files.createDirectories(backend.resolve("src/main/java"));
        Files.writeString(backend.resolve("build.gradle"), "plugins { id 'java' }");

        Path frontend = Files.createDirectory(container.resolve("frontend"));
        Files.writeString(frontend.resolve("package.json"), "{}");

        Path directUnity = Files.createDirectory(tempDirectory.resolve("direct-unity"));
        Files.createDirectory(directUnity.resolve("Assets"));
        Files.createDirectory(directUnity.resolve("ProjectSettings"));
        Files.createDirectories(directUnity.resolve("Packages"));
        Files.writeString(directUnity.resolve("Packages/manifest.json"), "{}");
        Files.createDirectories(directUnity.resolve("Assets/nested"));
        Files.writeString(directUnity.resolve("Assets/nested/package.json"), "{}");

        ProjectDiscoveryService service = new ProjectDiscoveryService(
                new WorkspaceProperties(tempDirectory),
                new ProjectTypeDetector()
        );

        WorkspaceDiscoveryResult result = service.discoverWorkspace();

        assertThat(result.projects())
                .extracting(DetectedProject::name)
                .containsExactly("backend", "frontend", "direct-unity");
        assertThat(result.projects())
                .extracting(DetectedProject::projectId)
                .containsExactly("container/backend", "container/frontend", "direct-unity");
        assertThat(result.containers())
                .extracting(DetectedContainer::name)
                .containsExactly("container");
        assertThat(result.containers().get(0).gitRepository()).isTrue();
        assertThat(result.containers().get(0).projects())
                .extracting(DetectedProject::name)
                .containsExactly("backend", "frontend");
    }

    @Test
    void doesNotSearchPastOneAdditionalLevel() throws IOException {
        Path wrapper = Files.createDirectory(tempDirectory.resolve("wrapper"));
        Path groupingFolder = Files.createDirectory(wrapper.resolve("grouping"));
        Path tooDeep = Files.createDirectory(groupingFolder.resolve("too-deep"));
        Files.writeString(tooDeep.resolve("package.json"), "{}");

        ProjectDiscoveryService service = new ProjectDiscoveryService(
                new WorkspaceProperties(tempDirectory),
                new ProjectTypeDetector()
        );

        WorkspaceDiscoveryResult result = service.discoverWorkspace();

        assertThat(result.containers()).isEmpty();
        assertThat(result.projects())
                .singleElement()
                .satisfies(project -> {
                    assertThat(project.name()).isEqualTo("wrapper");
                    assertThat(project.projectType()).isEqualTo(ProjectType.UNKNOWN);
                });
    }

    @Test
    void resolvesNestedProjectOnlyBySafeWorkspaceRelativeIdentifier() throws IOException {
        Path container = Files.createDirectory(tempDirectory.resolve("container"));
        Path nested = Files.createDirectory(container.resolve("backend"));
        Files.writeString(nested.resolve("package.json"), "{}");

        ProjectDiscoveryService service = new ProjectDiscoveryService(
                new WorkspaceProperties(tempDirectory),
                new ProjectTypeDetector()
        );

        assertThat(service.findProject(tempDirectory, "container/backend"))
                .get()
                .extracting(DetectedProject::projectId)
                .isEqualTo("container/backend");
        assertThat(service.findProject(tempDirectory, "container\\backend")).isPresent();
        assertThat(service.findProject(tempDirectory, "backend")).isEmpty();
        assertThat(service.findProject(tempDirectory, "container")).isEmpty();
        assertThat(service.findProject(tempDirectory, "../outside")).isEmpty();
        assertThat(service.findProject(tempDirectory, "container/../backend")).isEmpty();
    }
}
