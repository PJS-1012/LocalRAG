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
        DetectedProject project = discoveryService.discoverProjects().stream()
                .filter(candidate -> candidate.name().equalsIgnoreCase(projectName))
                .findFirst()
                .orElseThrow(() -> new WorkspaceAccessException(
                        "Direct child project was not found in the registered workspace: " + projectName));

        ScanAccumulator accumulator = new ScanAccumulator(project);
        try {
            Files.walkFileTree(project.rootPath(), accumulator);
        } catch (IOException exception) {
            throw new WorkspaceAccessException("Failed to scan project: " + project.rootPath(), exception);
        }
        return accumulator.result();
    }

    private final class ScanAccumulator extends SimpleFileVisitor<Path> {

        private final DetectedProject project;
        private final Map<FileScanStatus, Long> statusCounts = new EnumMap<>(FileScanStatus.class);
        private final List<String> excludedDirectories = new ArrayList<>();
        private final List<SkippedFile> tooLargeFiles = new ArrayList<>();

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
                long excludedCount = countFilesWithoutReadingContent(directory);
                increment(FileScanStatus.SKIPPED_EXCLUDED_PATH, excludedCount);
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
                increment(FileScanStatus.SKIPPED_SENSITIVE, 1);
            } else if (policy.isExcludedFile(file)) {
                increment(FileScanStatus.SKIPPED_EXTENSION, 1);
            } else if (!policy.isSupported(file)) {
                increment(FileScanStatus.SKIPPED_EXTENSION, 1);
            } else if (attributes.size() > policy.maxFileSizeBytes()) {
                increment(FileScanStatus.SKIPPED_TOO_LARGE, 1);
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
                increment(FileScanStatus.SUPPORTED, 1);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) {
            increment(FileScanStatus.METADATA_FAILED, 1);
            return FileVisitResult.CONTINUE;
        }

        private long countFilesWithoutReadingContent(Path directory) {
            try (var paths = Files.walk(directory)) {
                return paths.filter(Files::isRegularFile).count();
            } catch (IOException exception) {
                increment(FileScanStatus.METADATA_FAILED, 1);
                return 0;
            }
        }

        private void increment(FileScanStatus status, long count) {
            statusCounts.compute(status, (key, current) -> current + count);
        }

        private ProjectScanResult result() {
            long supported = statusCounts.get(FileScanStatus.SUPPORTED);
            long tooLarge = statusCounts.get(FileScanStatus.SKIPPED_TOO_LARGE);
            long failed = statusCounts.get(FileScanStatus.METADATA_FAILED);
            long excluded = statusCounts.get(FileScanStatus.SKIPPED_EXTENSION)
                    + statusCounts.get(FileScanStatus.SKIPPED_EXCLUDED_PATH)
                    + statusCounts.get(FileScanStatus.SKIPPED_SENSITIVE);
            long total = supported + tooLarge + failed + excluded;

            return new ProjectScanResult(
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
        }
    }
}
