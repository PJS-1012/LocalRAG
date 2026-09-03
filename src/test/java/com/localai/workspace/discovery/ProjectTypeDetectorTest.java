package com.localai.workspace.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectTypeDetectorTest {

    @TempDir
    Path tempDirectory;

    private final ProjectTypeDetector detector = new ProjectTypeDetector();

    @Test
    void detectsSpringBootProjectFromMultipleHints() throws IOException {
        Path project = Files.createDirectory(tempDirectory.resolve("spring-project"));
        Files.createDirectories(project.resolve("src/main/java"));
        Files.writeString(project.resolve("build.gradle"), "id 'org.springframework.boot' version '3.5.16'");
        Files.createDirectory(project.resolve(".git"));

        DetectedProject detected = detector.detect(project);

        assertThat(detected.projectType()).isEqualTo(ProjectType.JAVA);
        assertThat(detected.detectedFramework()).isEqualTo("SPRING_BOOT");
        assertThat(detected.gitRepository()).isTrue();
        assertThat(detected.detectionHints())
                .contains(".git", "build.gradle", "src/main/java");
    }

    @Test
    void detectsUnityOnlyWhenAllRequiredHintsExist() throws IOException {
        Path project = Files.createDirectory(tempDirectory.resolve("unity-project"));
        Files.createDirectory(project.resolve("Assets"));
        Files.createDirectory(project.resolve("ProjectSettings"));
        Files.createDirectories(project.resolve("Packages"));
        Files.writeString(project.resolve("Packages/manifest.json"), "{}");

        DetectedProject detected = detector.detect(project);

        assertThat(detected.projectType()).isEqualTo(ProjectType.UNITY);
        assertThat(detected.detectedFramework()).isEqualTo("UNITY");
    }

    @Test
    void doesNotInferFrameworkFromGitDirectoryAlone() throws IOException {
        Path project = Files.createDirectory(tempDirectory.resolve("git-only"));
        Files.createDirectory(project.resolve(".git"));

        DetectedProject detected = detector.detect(project);

        assertThat(detected.projectType()).isEqualTo(ProjectType.UNKNOWN);
        assertThat(detected.detectedFramework()).isEqualTo("UNKNOWN");
        assertThat(detected.gitRepository()).isTrue();
    }

    @Test
    void detectsJavaProjectFromSettingsAndSourceMarkers() throws IOException {
        Path project = Files.createDirectory(tempDirectory.resolve("settings-java"));
        Files.createDirectories(project.resolve("src/main/java"));
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'settings-java'");

        DetectedProject detected = detector.detect(project);

        assertThat(detected.projectType()).isEqualTo(ProjectType.JAVA);
        assertThat(detected.detectedFramework()).isEqualTo("JAVA");
        assertThat(detected.detectionHints()).contains("settings.gradle", "src/main/java");
    }
}
