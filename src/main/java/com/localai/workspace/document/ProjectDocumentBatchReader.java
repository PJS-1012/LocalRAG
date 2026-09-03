package com.localai.workspace.document;

import com.localai.workspace.scan.FileScanEntry;
import com.localai.workspace.scan.FileScanStatus;
import com.localai.workspace.scan.ProjectFileScanner;
import com.localai.workspace.scan.ProjectScanPlan;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProjectDocumentBatchReader {

    private final ProjectFileScanner fileScanner;
    private final ProjectDocumentReader documentReader;

    public ProjectDocumentBatchReader(
            ProjectFileScanner fileScanner,
            ProjectDocumentReader documentReader
    ) {
        this.fileScanner = fileScanner;
        this.documentReader = documentReader;
    }

    public ProjectDocumentReadResult read(String projectName) {
        long startedAt = System.nanoTime();
        return read(fileScanner.plan(projectName), startedAt);
    }

    public ProjectDocumentReadResult read(Path workspaceRoot, String projectName) {
        long startedAt = System.nanoTime();
        return read(fileScanner.plan(workspaceRoot, projectName), startedAt);
    }

    private ProjectDocumentReadResult read(ProjectScanPlan scanPlan, long startedAt) {
        List<WorkspaceDocument> documents = new ArrayList<>();
        List<ProjectFileReadResult> fileResults = new ArrayList<>();

        for (FileScanEntry file : scanPlan.files()) {
            if (file.status() == FileScanStatus.SUPPORTED) {
                DocumentReadResult readResult = documentReader.read(
                        scanPlan.project(),
                        file.relativePath()
                );
                if (readResult.status() == DocumentReadStatus.READ_SUCCESS) {
                    documents.add(readResult.document());
                }
                fileResults.add(new ProjectFileReadResult(
                        file.relativePath(),
                        file.absolutePath(),
                        readResult.status(),
                        readResult.reason()
                ));
            } else {
                fileResults.add(fromScan(file));
            }
        }

        long failedCount = fileResults.stream()
                .filter(result -> result.status() == DocumentReadStatus.READ_FAILED)
                .count();
        long successCount = documents.size();
        long skippedCount = fileResults.size() - successCount - failedCount;
        long totalTextBytes = documents.stream().mapToLong(WorkspaceDocument::size).sum();
        long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;

        return new ProjectDocumentReadResult(
                scanPlan.project().name(),
                scanPlan.project().projectId(),
                scanPlan.summary().totalDetectedFiles(),
                successCount,
                failedCount,
                skippedCount,
                totalTextBytes,
                durationMillis,
                List.copyOf(documents),
                List.copyOf(fileResults)
        );
    }

    private ProjectFileReadResult fromScan(FileScanEntry file) {
        DocumentReadStatus status = switch (file.status()) {
            case SKIPPED_EXTENSION -> DocumentReadStatus.SKIPPED_EXTENSION;
            case SKIPPED_EXCLUDED_PATH -> DocumentReadStatus.SKIPPED_EXCLUDED_PATH;
            case SKIPPED_SENSITIVE -> DocumentReadStatus.SKIPPED_SENSITIVE;
            case SKIPPED_TOO_LARGE -> DocumentReadStatus.SKIPPED_TOO_LARGE;
            case METADATA_FAILED -> DocumentReadStatus.READ_FAILED;
            case SUPPORTED -> throw new IllegalArgumentException("Supported files must be read");
        };
        return new ProjectFileReadResult(
                file.relativePath(),
                file.absolutePath(),
                status,
                file.reason()
        );
    }
}
