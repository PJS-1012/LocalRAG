package com.localai.workspace.scan;

import com.localai.workspace.discovery.DetectedProject;
import com.localai.workspace.discovery.ProjectDiscoveryService;
import com.localai.workspace.discovery.ProjectType;
import com.localai.workspace.discovery.WorkspaceAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceMetadataScanServiceTest {

    @TempDir
    Path workspaceRoot;

    @Test
    void continuesWhenOneProjectScanFails() {
        ProjectDiscoveryService discoveryService = mock(ProjectDiscoveryService.class);
        ProjectFileScanner projectFileScanner = mock(ProjectFileScanner.class);
        DetectedProject healthy = project("healthy", ProjectType.JAVA, "SPRING_BOOT", true);
        DetectedProject broken = project("broken", ProjectType.UNKNOWN, "UNKNOWN", false);
        when(discoveryService.discoverProjects(workspaceRoot)).thenReturn(List.of(healthy, broken));
        when(projectFileScanner.scan(workspaceRoot, "healthy")).thenReturn(scanResult());
        when(projectFileScanner.scan(workspaceRoot, "broken"))
                .thenThrow(new WorkspaceAccessException("access denied"));

        WorkspaceScanSummary result = new WorkspaceMetadataScanService(
                discoveryService,
                projectFileScanner
        ).scan(workspaceRoot);

        assertThat(result.totalProjects()).isEqualTo(2);
        assertThat(result.successfulProjects()).isEqualTo(1);
        assertThat(result.failedProjects()).isEqualTo(1);
        assertThat(result.totalFiles()).isEqualTo(10);
        assertThat(result.includedFiles()).isEqualTo(4);
        assertThat(result.excludedFiles()).isEqualTo(5);
        assertThat(result.failedFiles()).isEqualTo(1);
        assertThat(result.skippedTooLargeFiles()).isZero();
        assertThat(result.projects())
                .filteredOn(project -> project.status() == WorkspaceProjectScanStatus.FAILED)
                .singleElement()
                .satisfies(project -> {
                    assertThat(project.projectName()).isEqualTo("broken");
                    assertThat(project.failureReason()).contains("access denied");
                });
        verify(projectFileScanner).scan(workspaceRoot, "healthy");
        verify(projectFileScanner).scan(workspaceRoot, "broken");
    }

    private DetectedProject project(
            String name,
            ProjectType type,
            String framework,
            boolean gitRepository
    ) {
        return new DetectedProject(
                name,
                workspaceRoot.resolve(name),
                type,
                framework,
                gitRepository,
                true,
                gitRepository ? List.of(".git") : List.of()
        );
    }

    private ProjectScanResult scanResult() {
        return new ProjectScanResult(
                "healthy",
                workspaceRoot.resolve("healthy").toString(),
                ProjectType.JAVA,
                10,
                4,
                5,
                1,
                0,
                Map.of(),
                List.of("build"),
                List.of()
        );
    }
}
