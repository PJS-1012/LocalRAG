package com.localai.workspace.document;

import com.localai.workspace.discovery.ProjectDiscoveryService;
import com.localai.workspace.discovery.ProjectTypeDetector;
import com.localai.workspace.discovery.WorkspaceProperties;
import com.localai.workspace.scan.WorkspaceScanPolicy;
import com.localai.workspace.scan.WorkspaceScanProperties;
import org.junit.jupiter.api.BeforeEach;
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

class ProjectDocumentReaderTest {

    @TempDir
    Path workspaceRoot;

    private Path projectRoot;
    private ProjectDocumentReader reader;

    @BeforeEach
    void setUp() throws IOException {
        projectRoot = Files.createDirectory(workspaceRoot.resolve("sample-project"));
        Files.createDirectories(projectRoot.resolve("src/main/java"));
        Files.writeString(projectRoot.resolve("build.gradle"), "plugins { id 'java' }");

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
                new WorkspaceProperties(workspaceRoot),
                new ProjectTypeDetector()
        );
        reader = new ProjectDocumentReader(discoveryService, new WorkspaceScanPolicy(properties));
    }

    @Test
    void readsOneUtf8FileAndPreservesMetadata() throws IOException {
        Path readme = projectRoot.resolve("README.md");
        Files.writeString(readme, "# Sample Project\nStep 6 document reader", StandardCharsets.UTF_8);

        DocumentReadResult result = reader.read("sample-project", "README.md");

        assertThat(result.status()).isEqualTo(DocumentReadStatus.READ_SUCCESS);
        assertThat(result.reason()).isNull();
        assertThat(result.document()).isNotNull();
        assertThat(result.document().projectName()).isEqualTo("sample-project");
        assertThat(result.document().fileName()).isEqualTo("README.md");
        assertThat(result.document().filePath()).isEqualTo(readme.toRealPath().toString());
        assertThat(result.document().extension()).isEqualTo("md");
        assertThat(result.document().size()).isEqualTo(Files.size(readme));
        assertThat(result.document().modifiedAt()).isNotNull();
        assertThat(result.document().content()).contains("Step 6 document reader");
    }

    @Test
    void reappliesScanPolicyAndRejectsPathEscape() throws IOException {
        Files.writeString(projectRoot.resolve(".env"), "TOKEN=secret");
        Files.createDirectories(projectRoot.resolve(".git"));
        Files.writeString(projectRoot.resolve(".git/config.md"), "private");
        Files.writeString(projectRoot.resolve("image.png"), "binary-like");
        Files.writeString(projectRoot.resolve("large.md"), "x".repeat(65));
        Files.writeString(workspaceRoot.resolve("outside.md"), "outside");

        assertThat(reader.read("sample-project", ".env").status())
                .isEqualTo(DocumentReadStatus.SKIPPED_SENSITIVE);
        assertThat(reader.read("sample-project", ".git/config.md").status())
                .isEqualTo(DocumentReadStatus.SKIPPED_EXCLUDED_PATH);
        assertThat(reader.read("sample-project", "image.png").status())
                .isEqualTo(DocumentReadStatus.SKIPPED_EXTENSION);
        assertThat(reader.read("sample-project", "large.md").status())
                .isEqualTo(DocumentReadStatus.SKIPPED_TOO_LARGE);
        assertThat(reader.read("sample-project", "../outside.md").status())
                .isEqualTo(DocumentReadStatus.READ_FAILED);
    }

    @Test
    void reportsInvalidUtf8WithoutThrowing() throws IOException {
        Files.write(projectRoot.resolve("invalid.md"), new byte[]{(byte) 0xC3, (byte) 0x28});

        DocumentReadResult result = reader.read("sample-project", "invalid.md");

        assertThat(result.status()).isEqualTo(DocumentReadStatus.READ_FAILED);
        assertThat(result.document()).isNull();
        assertThat(result.reason()).contains("UTF-8 file read failed");
    }
}
