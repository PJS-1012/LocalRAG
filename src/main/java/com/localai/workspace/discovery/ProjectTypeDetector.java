package com.localai.workspace.discovery;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Component
public class ProjectTypeDetector {

    public DetectedProject detect(Path projectRoot) {
        List<String> hints = new ArrayList<>();
        boolean git = directoryExists(projectRoot, ".git");
        addHint(hints, git, ".git");

        boolean unityAssets = directoryExists(projectRoot, "Assets");
        boolean unitySettings = directoryExists(projectRoot, "ProjectSettings");
        boolean unityManifest = fileExists(projectRoot, "Packages/manifest.json");
        addHint(hints, unityAssets, "Assets");
        addHint(hints, unitySettings, "ProjectSettings");
        addHint(hints, unityManifest, "Packages/manifest.json");

        boolean gradle = fileExists(projectRoot, "build.gradle");
        boolean gradleKts = fileExists(projectRoot, "build.gradle.kts");
        boolean maven = fileExists(projectRoot, "pom.xml");
        boolean gradleSettings = fileExists(projectRoot, "settings.gradle");
        boolean gradleSettingsKts = fileExists(projectRoot, "settings.gradle.kts");
        boolean javaSource = directoryExists(projectRoot, "src/main/java");
        addHint(hints, gradle, "build.gradle");
        addHint(hints, gradleKts, "build.gradle.kts");
        addHint(hints, maven, "pom.xml");
        addHint(hints, gradleSettings, "settings.gradle");
        addHint(hints, gradleSettingsKts, "settings.gradle.kts");
        addHint(hints, javaSource, "src/main/java");

        boolean node = fileExists(projectRoot, "package.json");
        boolean python = fileExists(projectRoot, "pyproject.toml")
                || fileExists(projectRoot, "requirements.txt");
        boolean dotnet = containsRootFileWithExtension(projectRoot, ".sln")
                || containsRootFileWithExtension(projectRoot, ".csproj");
        addHint(hints, node, "package.json");
        addHint(hints, python, "Python build file");
        addHint(hints, dotnet, ".sln/.csproj");

        ProjectType type;
        String framework;
        if (unityAssets && unitySettings && unityManifest) {
            type = ProjectType.UNITY;
            framework = "UNITY";
        } else if ((gradle || gradleKts || maven || gradleSettings || gradleSettingsKts) && javaSource) {
            type = ProjectType.JAVA;
            framework = isSpringBootProject(projectRoot, gradle, gradleKts, maven)
                    ? "SPRING_BOOT"
                    : "JAVA";
        } else if (node) {
            type = ProjectType.NODE;
            framework = "NODE";
        } else if (dotnet) {
            type = ProjectType.DOTNET;
            framework = "DOTNET";
        } else if (python) {
            type = ProjectType.PYTHON;
            framework = "PYTHON";
        } else {
            type = ProjectType.UNKNOWN;
            framework = "UNKNOWN";
        }

        return new DetectedProject(
                projectRoot.getFileName().toString(),
                projectRoot.toAbsolutePath().normalize(),
                type,
                framework,
                git,
                true,
                List.copyOf(hints)
        );
    }

    private boolean isSpringBootProject(Path root, boolean gradle, boolean gradleKts, boolean maven) {
        return (gradle && containsText(root.resolve("build.gradle"), "org.springframework.boot"))
                || (gradleKts && containsText(root.resolve("build.gradle.kts"), "org.springframework.boot"))
                || (maven && containsText(root.resolve("pom.xml"), "spring-boot"));
    }

    private boolean containsText(Path file, String text) {
        try {
            return Files.readString(file).toLowerCase(Locale.ROOT)
                    .contains(text.toLowerCase(Locale.ROOT));
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean containsRootFileWithExtension(Path root, String extension) {
        try (Stream<Path> entries = Files.list(root)) {
            return entries.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension));
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean fileExists(Path root, String relativePath) {
        return Files.isRegularFile(root.resolve(relativePath));
    }

    private boolean directoryExists(Path root, String relativePath) {
        return Files.isDirectory(root.resolve(relativePath));
    }

    private void addHint(List<String> hints, boolean detected, String hint) {
        if (detected) {
            hints.add(hint);
        }
    }
}
