package com.localai.workspace.document;

import com.localai.workspace.discovery.ProjectDiscoveryService;
import com.localai.workspace.discovery.ProjectTypeDetector;
import com.localai.workspace.discovery.WorkspaceProperties;
import com.localai.workspace.scan.ProjectFileScanner;
import com.localai.workspace.scan.WorkspaceScanPolicy;
import com.localai.workspace.scan.WorkspaceScanProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectDocumentBatchReaderTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void readsAllowedFilesAndContinuesAfterOneFileFails() throws IOException {
        Path project = Files.createDirectory(workspaceRoot.resolve("sample-project"));
        Files.createDirectories(project.resolve("src/main/java"));
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }");
        Path readme = project.resolve("README.md");
        Path source = project.resolve("src/main/java/Sample.java");
        Files.writeString(readme, "# Sample", StandardCharsets.UTF_8);
        Files.writeString(source, "class Sample {}", StandardCharsets.UTF_8);
        Files.write(project.resolve("invalid.md"), new byte[]{(byte) 0xC3, (byte) 0x28});
        Files.writeString(project.resolve(".env"), "TOKEN=secret");
        Files.createDirectory(project.resolve(".git"));
        Files.writeString(project.resolve(".git/config.md"), "private");
        Files.writeString(project.resolve("large.md"), "x".repeat(65));
        Files.writeString(project.resolve("image.png"), "binary-like");

        ProjectDocumentBatchReader batchReader = batchReader(workspaceRoot);

        ProjectDocumentReadResult result = batchReader.read(workspaceRoot, "sample-project");

        assertThat(result.projectName()).isEqualTo("sample-project");
        assertThat(result.totalFiles()).isEqualTo(8);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(5);
        assertThat(result.totalTextBytes()).isEqualTo(Files.size(readme) + Files.size(source));
        assertThat(result.durationMillis()).isNotNegative();
        assertThat(result.documents())
                .extracting(WorkspaceDocument::fileName)
                .containsExactlyInAnyOrder("README.md", "Sample.java");
        assertThat(result.documents())
                .extracting(WorkspaceDocument::content)
                .anyMatch(content -> content.contains("class Sample"));
        assertThat(result.fileResults()).hasSize(8);
        assertThat(result.fileResults())
                .filteredOn(file -> file.status() == DocumentReadStatus.READ_FAILED)
                .singleElement()
                .satisfies(file -> {
                    assertThat(file.absolutePath()).endsWith("invalid.md");
                    assertThat(file.reason()).contains("UTF-8 file read failed");
                });
        assertThat(result.fileResults())
                .filteredOn(file -> file.status() == DocumentReadStatus.SKIPPED_SENSITIVE)
                .singleElement()
                .satisfies(file -> {
                    assertThat(file.absolutePath()).endsWith(".env");
                    assertThat(file.reason()).contains("Sensitive");
                });
        assertThat(result.fileResults())
                .filteredOn(file -> file.status() == DocumentReadStatus.SKIPPED_TOO_LARGE)
                .singleElement()
                .satisfies(file -> {
                    assertThat(file.absolutePath()).endsWith("large.md");
                    assertThat(file.reason()).contains("64 bytes");
                });
        assertThat(result.documents())
                .extracting(WorkspaceDocument::fileName)
                .doesNotContain(".env");
    }

    private ProjectDocumentBatchReader batchReader(Path defaultWorkspaceRoot) {
        WorkspaceScanProperties properties = new WorkspaceScanProperties(
                DataSize.ofBytes(64),
                Set.of("md", "java"),
                Set.of("readme"),
                Set.of(".git", "build"),
                Set.of("logs/archive"),
                Set.of("library", "temp"),
                List.of("*.png"),
                List.of(".env", ".env.*", "*.key")
        );
        ProjectDiscoveryService discoveryService = new ProjectDiscoveryService(
                new WorkspaceProperties(defaultWorkspaceRoot),
                new ProjectTypeDetector()
        );
        WorkspaceScanPolicy policy = new WorkspaceScanPolicy(properties);
        ProjectFileScanner scanner = new ProjectFileScanner(discoveryService, policy);
        ProjectDocumentReader reader = new ProjectDocumentReader(discoveryService, policy);
        return new ProjectDocumentBatchReader(scanner, reader);
    }
}
