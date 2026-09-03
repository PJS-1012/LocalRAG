package com.localai.workspace.scan;

import com.localai.workspace.discovery.ProjectDiscoveryService;
import com.localai.workspace.discovery.ProjectTypeDetector;
import com.localai.workspace.discovery.WorkspaceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectFileScannerTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void appliesUnityExclusionsSensitiveFilesAndSizeLimit() throws IOException {
        Path project = Files.createDirectory(workspaceRoot.resolve("game"));
        Files.createDirectory(project.resolve("Assets"));
        Files.createDirectory(project.resolve("ProjectSettings"));
        Files.createDirectories(project.resolve("Packages"));
        Files.writeString(project.resolve("Packages/manifest.json"), "{}");
        Files.writeString(project.resolve("Assets/Player.cs"), "class Player {}");
        Files.createDirectories(project.resolve("Library/cache"));
        Files.writeString(project.resolve("Library/cache/generated.bin"), "generated");
        Files.createDirectories(project.resolve(".git/objects"));
        Files.writeString(project.resolve(".git/objects/internal"), "git data");
        Files.writeString(project.resolve(".env"), "API_KEY=secret");
        Files.writeString(project.resolve("large.log"), "this file is over ten bytes");
        Files.createDirectories(project.resolve("logs/archive"));
        Files.writeString(project.resolve("logs/archive/old.log"), "old log");
        Files.createDirectory(project.resolve("archive"));
        Files.writeString(project.resolve("archive/design.md"), "design");
        Files.writeString(project.resolve("image.png"), "not a real image");

        WorkspaceScanProperties scanProperties = new WorkspaceScanProperties(
                DataSize.ofBytes(20),
                Set.of("cs", "json", "log", "md"),
                Set.of("dockerfile", "readme", "license"),
                Set.of(".git", "build"),
                Set.of("logs/archive"),
                Set.of("library", "temp"),
                List.of("*.png"),
                List.of(".env", ".env.*", "*.pem", "*.key")
        );
        ProjectDiscoveryService discoveryService = new ProjectDiscoveryService(
                new WorkspaceProperties(workspaceRoot),
                new ProjectTypeDetector()
        );
        ProjectFileScanner scanner = new ProjectFileScanner(
                discoveryService,
                new WorkspaceScanPolicy(scanProperties)
        );

        ProjectScanResult result = scanner.scan("game");

        assertThat(result.projectType()).isEqualTo(com.localai.workspace.discovery.ProjectType.UNITY);
        assertThat(result.totalDetectedFiles()).isEqualTo(9);
        assertThat(result.supportedFiles()).isEqualTo(3);
        assertThat(result.excludedFiles()).isEqualTo(5);
        assertThat(result.skippedTooLargeFiles()).isEqualTo(1);
        assertThat(result.failedFiles()).isZero();
        assertThat(result.excludedDirectories()).contains("Library", ".git");
        assertThat(result.excludedDirectories()).anyMatch(path -> path.replace('\\', '/').equals("logs/archive"));
        assertThat(result.tooLargeFiles())
                .extracting(SkippedFile::file)
                .containsExactly("large.log");
    }

    @Test
    void scansNestedUnityRootWithUnityExclusions() throws IOException {
        Path container = Files.createDirectory(workspaceRoot.resolve("container"));
        Path project = Files.createDirectory(container.resolve("game"));
        Files.createDirectory(project.resolve("Assets"));
        Files.createDirectory(project.resolve("ProjectSettings"));
        Files.createDirectories(project.resolve("Packages"));
        Files.writeString(project.resolve("Packages/manifest.json"), "{}");
        Files.writeString(project.resolve("Assets/Player.cs"), "class Player {}");
        Files.createDirectories(project.resolve("Library/Bee"));
        Files.writeString(project.resolve("Library/Bee/oversized.json"), "this generated file exceeds limit");

        WorkspaceScanProperties scanProperties = new WorkspaceScanProperties(
                DataSize.ofBytes(20),
                Set.of("cs", "json"),
                Set.of(),
                Set.of(".git", "build", "node_modules"),
                Set.of(),
                Set.of("library"),
                List.of(),
                List.of()
        );
        ProjectDiscoveryService discoveryService = new ProjectDiscoveryService(
                new WorkspaceProperties(workspaceRoot),
                new ProjectTypeDetector()
        );
        ProjectFileScanner scanner = new ProjectFileScanner(
                discoveryService,
                new WorkspaceScanPolicy(scanProperties)
        );

        ProjectScanResult result = scanner.scan("container/game");

        assertThat(result.rootPath()).isEqualTo(project.toAbsolutePath().normalize().toString());
        assertThat(result.projectType()).isEqualTo(com.localai.workspace.discovery.ProjectType.UNITY);
        assertThat(result.excludedDirectories()).contains("Library");
        assertThat(result.skippedTooLargeFiles()).isZero();
        assertThat(result.tooLargeFiles()).isEmpty();
    }
}
