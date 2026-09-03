package com.localai.workspace.scan;

import com.localai.workspace.discovery.DetectedProject;
import com.localai.workspace.discovery.ProjectDiscoveryService;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkspaceMetadataScanService {

    private final ProjectDiscoveryService discoveryService;
    private final ProjectFileScanner projectFileScanner;

    public WorkspaceMetadataScanService(
            ProjectDiscoveryService discoveryService,
            ProjectFileScanner projectFileScanner
    ) {
        this.discoveryService = discoveryService;
        this.projectFileScanner = projectFileScanner;
    }

    public WorkspaceScanSummary scan() {
        return scan(discoveryService.defaultWorkspaceRoot());
    }

    public WorkspaceScanSummary scan(Path workspacePath) {
        long workspaceStartedAt = System.nanoTime();
        Path workspaceRoot = workspacePath.toAbsolutePath().normalize();
        List<DetectedProject> detectedProjects = discoveryService.discoverProjects(workspaceRoot);
        List<WorkspaceProjectScanResult> projectResults = new ArrayList<>();

        for (DetectedProject project : detectedProjects) {
            long projectStartedAt = System.nanoTime();
            try {
                ProjectScanResult scan = projectFileScanner.scan(project);
                projectResults.add(success(project, scan, elapsedMillis(projectStartedAt)));
            } catch (RuntimeException exception) {
                projectResults.add(failure(project, exception, elapsedMillis(projectStartedAt)));
            }
        }

        long successfulProjects = projectResults.stream()
                .filter(project -> project.status() == WorkspaceProjectScanStatus.SUCCESS)
                .count();
        long failedProjects = projectResults.size() - successfulProjects;

        return new WorkspaceScanSummary(
                workspaceRoot.toString(),
                projectResults.size(),
                successfulProjects,
                failedProjects,
                sum(projectResults, Metric.TOTAL),
                sum(projectResults, Metric.INCLUDED),
                sum(projectResults, Metric.EXCLUDED),
                sum(projectResults, Metric.FAILED),
                sum(projectResults, Metric.TOO_LARGE),
                elapsedMillis(workspaceStartedAt),
                List.copyOf(projectResults)
        );
    }

    private WorkspaceProjectScanResult success(
            DetectedProject project,
            ProjectScanResult scan,
            long durationMillis
    ) {
        return new WorkspaceProjectScanResult(
                project.name(),
                project.projectType(),
                project.detectedFramework(),
                project.gitRepository(),
                project.detectionHints(),
                WorkspaceProjectScanStatus.SUCCESS,
                null,
                scan.totalDetectedFiles(),
                scan.supportedFiles(),
                scan.excludedFiles(),
                scan.failedFiles(),
                scan.skippedTooLargeFiles(),
                scan.tooLargeFiles(),
                durationMillis
        );
    }

    private WorkspaceProjectScanResult failure(
            DetectedProject project,
            RuntimeException exception,
            long durationMillis
    ) {
        String message = exception.getMessage();
        String reason = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return new WorkspaceProjectScanResult(
                project.name(),
                project.projectType(),
                project.detectedFramework(),
                project.gitRepository(),
                project.detectionHints(),
                WorkspaceProjectScanStatus.FAILED,
                reason,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                durationMillis
        );
    }

    private long sum(List<WorkspaceProjectScanResult> projects, Metric metric) {
        return projects.stream().mapToLong(metric::value).sum();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private enum Metric {
        TOTAL {
            @Override
            long value(WorkspaceProjectScanResult project) {
                return project.totalFiles();
            }
        },
        INCLUDED {
            @Override
            long value(WorkspaceProjectScanResult project) {
                return project.includedFiles();
            }
        },
        EXCLUDED {
            @Override
            long value(WorkspaceProjectScanResult project) {
                return project.excludedFiles();
            }
        },
        FAILED {
            @Override
            long value(WorkspaceProjectScanResult project) {
                return project.failedFiles();
            }
        },
        TOO_LARGE {
            @Override
            long value(WorkspaceProjectScanResult project) {
                return project.skippedTooLargeFiles();
            }
        };

        abstract long value(WorkspaceProjectScanResult project);
    }
}
