package com.localai.workspace.index;

import com.localai.workspace.embedding.ChunkEmbeddingOutcome;
import com.localai.workspace.embedding.EmbeddingRunStatus;
import com.localai.workspace.embedding.EmbeddedChunk;
import com.localai.workspace.embedding.ProjectChunkEmbeddingResult;
import com.localai.workspace.embedding.ProjectChunkEmbeddingService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectIndexService {

    private final ProjectChunkEmbeddingService embeddingService;
    private final ProjectIndexRepository repository;
    private final IndexingProperties properties;

    public ProjectIndexService(
            ProjectChunkEmbeddingService embeddingService,
            ProjectIndexRepository repository,
            IndexingProperties properties
    ) {
        this.embeddingService = embeddingService;
        this.repository = repository;
        this.properties = properties;
    }

    public ProjectIndexResult index(String projectId) {
        long startedAt = System.nanoTime();
        ProjectChunkEmbeddingResult embeddingResult = embeddingService.embed(projectId);

        if (embeddingResult.status() != EmbeddingRunStatus.SUCCESS
                || embeddingResult.failedCount() > 0
                || embeddingResult.embeddedCount() != embeddingResult.chunkCount()) {
            return failure(
                    embeddingResult,
                    ProjectIndexStatus.EMBEDDING_FAILED,
                    embeddingResult.providerFailureReason() == null
                            ? "Project Embedding did not complete successfully"
                            : embeddingResult.providerFailureReason(),
                    startedAt
            );
        }
        if (embeddingResult.dimensions() != properties.expectedDimensions()) {
            return failure(
                    embeddingResult,
                    ProjectIndexStatus.DIMENSION_MISMATCH,
                    "DB schema expects dimension " + properties.expectedDimensions()
                            + " but Embedding returned " + embeddingResult.dimensions(),
                    startedAt
            );
        }

        List<EmbeddedChunk> embeddedChunks = embeddingResult.outcomes().stream()
                .map(ChunkEmbeddingOutcome::embeddedChunk)
                .toList();
        if (embeddedChunks.stream().anyMatch(
                chunk -> chunk.dimensions() != properties.expectedDimensions())) {
            return failure(
                    embeddingResult,
                    ProjectIndexStatus.DIMENSION_MISMATCH,
                    "At least one Chunk does not match DB dimension "
                            + properties.expectedDimensions(),
                    startedAt
            );
        }

        try {
            ProjectIndexWriteResult writeResult = repository.synchronize(
                    embeddingResult.projectId(),
                    embeddedChunks
            );
            return new ProjectIndexResult(
                    embeddingResult.projectId(),
                    embeddingResult.documentCount(),
                    embeddingResult.chunkCount(),
                    embeddingResult.embeddedCount(),
                    writeResult.writtenCount(),
                    writeResult.storedCount(),
                    writeResult.deletedCount(),
                    embeddingResult.failedCount(),
                    embeddingResult.dimensions(),
                    embeddingResult.embeddingModel(),
                    embeddingResult.chunkingDurationMillis(),
                    embeddingResult.embeddingDurationMillis(),
                    writeResult.durationMillis(),
                    elapsedMillis(startedAt),
                    ProjectIndexStatus.SUCCESS,
                    null
            );
        } catch (RuntimeException exception) {
            return failure(
                    embeddingResult,
                    ProjectIndexStatus.STORAGE_FAILED,
                    conciseReason(exception),
                    startedAt
            );
        }
    }

    public ProjectIndexStats stats(String projectId) {
        return repository.stats(projectId).orElseGet(() -> ProjectIndexStats.empty(projectId));
    }

    private ProjectIndexResult failure(
            ProjectChunkEmbeddingResult embeddingResult,
            ProjectIndexStatus status,
            String reason,
            long startedAt
    ) {
        return new ProjectIndexResult(
                embeddingResult.projectId(),
                embeddingResult.documentCount(),
                embeddingResult.chunkCount(),
                embeddingResult.embeddedCount(),
                0,
                safeStoredCount(embeddingResult.projectId()),
                0,
                embeddingResult.failedCount(),
                embeddingResult.dimensions(),
                embeddingResult.embeddingModel(),
                embeddingResult.chunkingDurationMillis(),
                embeddingResult.embeddingDurationMillis(),
                0,
                elapsedMillis(startedAt),
                status,
                reason
        );
    }

    private long safeStoredCount(String projectId) {
        try {
            return repository.count(projectId);
        } catch (RuntimeException exception) {
            return -1;
        }
    }
    private String conciseReason(Throwable throwable) {
        String message = throwable.getMessage();
        String reason = throwable.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
