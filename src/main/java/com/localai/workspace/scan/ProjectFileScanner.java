package com.localai.workspace.scan;

import com.localai.workspace.discovery.DetectedProject;
import com.localai.workspace.discovery.ProjectDiscoveryService;
import com.localai.workspace.discovery.WorkspaceAccessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class ProjectFileScanner {

    private final ProjectDiscoveryService discoveryService;
    private final WorkspaceScanPolicy policy;

    public ProjectFileScanner(ProjectDiscoveryService discoveryService, WorkspaceScanPolicy policy) {
        this.discoveryService = discoveryService;
        this.policy = policy;
    }

    public ProjectScanResult scan(String projectName) {
        return scan(discoveryService.defaultWorkspaceRoot(), projectName);
    }

    public ProjectScanResult scan(Path workspaceRoot, String projectName) {
        return plan(workspaceRoot, projectName).summary();
    }

    public ProjectScanPlan plan(String projectName) {
        return plan(discoveryService.defaultWorkspaceRoot(), projectName);
    }

    public ProjectScanPlan plan(Path workspaceRoot, String projectName) {
        DetectedProject project = discoveryService.findProject(workspaceRoot, projectName)
                .orElseThrow(() -> new WorkspaceAccessException(
                        "Direct child project was not found in the registered workspace: " + projectName));

        ScanAccumulator accumulator = new ScanAccumulator(project);
        try {
            Files.walkFileTree(project.rootPath(), accumulator);
        } catch (IOException exception) {
            throw new WorkspaceAccessException("Failed to scan project: " + project.rootPath(), exception);
        }
        return accumulator.plan();
    }

    private final class ScanAccumulator extends SimpleFileVisitor<Path> {

        private final DetectedProject project;
        private final Map<FileScanStatus, Long> statusCounts = new EnumMap<>(FileScanStatus.class);
        private final List<String> excludedDirectories = new ArrayList<>();
        private final List<SkippedFile> tooLargeFiles = new ArrayList<>();
        private final List<FileScanEntry> fileEntries = new ArrayList<>();

        private ScanAccumulator(DetectedProject project) {
            this.project = project;
            for (FileScanStatus status : FileScanStatus.values()) {
                statusCounts.put(status, 0L);
            }
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            if (!directory.equals(project.rootPath())
                    && policy.isExcludedDirectory(directory, project.rootPath(), project.projectType())) {
                excludedDirectories.add(project.rootPath().relativize(directory).toString());
                collectExcludedFiles(directory);
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (!attributes.isRegularFile()) {
                return FileVisitResult.CONTINUE;
            }
            if (policy.isSensitiveFile(file)) {
                recordFile(file, attributes, FileScanStatus.SKIPPED_SENSITIVE, "Sensitive file policy");
            } else if (policy.isExcludedFile(file)) {
                recordFile(file, attributes, FileScanStatus.SKIPPED_EXTENSION, "Excluded file pattern");
            } else if (!policy.isSupported(file)) {
                recordFile(file, attributes, FileScanStatus.SKIPPED_EXTENSION, "Unsupported file extension");
            } else if (attributes.size() > policy.maxFileSizeBytes()) {
                recordFile(
                        file,
                        attributes,
                        FileScanStatus.SKIPPED_TOO_LARGE,
                        "current limit = " + policy.maxFileSizeBytes() + " bytes"
                );
                tooLargeFiles.add(new SkippedFile(
                        project.name(),
                        file.getFileName().toString(),
                        attributes.size(),
                        policy.extension(file),
                        file.toAbsolutePath().normalize().toString(),
                        FileScanStatus.SKIPPED_TOO_LARGE,
                        "current limit = " + policy.maxFileSizeBytes() + " bytes"
                ));
            } else {
                recordFile(file, attributes, FileScanStatus.SUPPORTED, null);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) {
            recordMetadataFailure(file, exception);
            return FileVisitResult.CONTINUE;
        }

        private void collectExcludedFiles(Path directory) {
            try {
                Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        if (attributes.isRegularFile()) {
                            recordFile(file, attributes, FileScanStatus.SKIPPED_EXCLUDED_PATH, "Excluded directory");
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exception) {
                        recordMetadataFailure(file, exception);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException exception) {
                recordMetadataFailure(directory, exception);
            }
        }

        private void increment(FileScanStatus status, long count) {
            statusCounts.compute(status, (key, current) -> current + count);
        }

        private void recordFile(
                Path file,
                BasicFileAttributes attributes,
                FileScanStatus status,
                String reason
        ) {
            Path absolutePath = file.toAbsolutePath().normalize();
            fileEntries.add(new FileScanEntry(
                    project.rootPath().relativize(absolutePath).toString(),
                    absolutePath.toString(),
                    file.getFileName().toString(),
                    policy.extension(file),
                    attributes.size(),
                    status,
                    reason
            ));
            increment(status, 1);
        }

        private void recordMetadataFailure(Path file, Exception exception) {
            Path absolutePath = file.toAbsolutePath().normalize();
            fileEntries.add(new FileScanEntry(
                    project.rootPath().relativize(absolutePath).toString(),
                    absolutePath.toString(),
                    file.getFileName().toString(),
                    policy.extension(file),
                    -1,
                    FileScanStatus.METADATA_FAILED,
                    exception.getClass().getSimpleName()
            ));
            increment(FileScanStatus.METADATA_FAILED, 1);
        }

        private ProjectScanPlan plan() {
            long supported = statusCounts.get(FileScanStatus.SUPPORTED);
            long tooLarge = statusCounts.get(FileScanStatus.SKIPPED_TOO_LARGE);
            long failed = statusCounts.get(FileScanStatus.METADATA_FAILED);
            long excluded = statusCounts.get(FileScanStatus.SKIPPED_EXTENSION)
                    + statusCounts.get(FileScanStatus.SKIPPED_EXCLUDED_PATH)
                    + statusCounts.get(FileScanStatus.SKIPPED_SENSITIVE);
            long total = supported + tooLarge + failed + excluded;

            ProjectScanResult summary = new ProjectScanResult(
                    project.name(),
                    project.rootPath().toString(),
                    project.projectType(),
                    total,
                    supported,
                    excluded,
                    failed,
                    tooLarge,
                    Map.copyOf(statusCounts),
                    List.copyOf(excludedDirectories),
                    List.copyOf(tooLargeFiles)
            );
            return new ProjectScanPlan(project, summary, List.copyOf(fileEntries));
        }
    }
}
