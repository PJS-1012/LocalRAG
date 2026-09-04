package com.localai.workspace.embedding;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces/projects/chunks/embeddings")
public class ProjectChunkEmbeddingController {

    private final ProjectChunkEmbeddingService projectEmbeddingService;
    private final EmbeddingService embeddingService;

    public ProjectChunkEmbeddingController(
            ProjectChunkEmbeddingService projectEmbeddingService,
            EmbeddingService embeddingService
    ) {
        this.projectEmbeddingService = projectEmbeddingService;
        this.embeddingService = embeddingService;
    }

    @PostMapping("/preview")
    public ProjectChunkEmbeddingPreview preview(@RequestParam String projectId) {
        ProjectChunkEmbeddingResult result = projectEmbeddingService.embed(projectId);
        List<EmbeddedChunkPreview> chunks = result.outcomes().stream()
                .filter(outcome -> outcome.embeddedChunk() != null)
                .map(outcome -> {
                    EmbeddedChunk embedded = outcome.embeddedChunk();
                    return new EmbeddedChunkPreview(
                            embedded.chunk().chunkId(),
                            embedded.chunk().sourceFilePath(),
                            embedded.chunk().chunkIndex(),
                            embedded.dimensions(),
                            embeddingService.preview(embedded.vector())
                    );
                })
                .toList();
        List<ChunkEmbeddingFailurePreview> failures = result.outcomes().stream()
                .filter(outcome -> outcome.embeddedChunk() == null)
                .map(outcome -> new ChunkEmbeddingFailurePreview(
                        outcome.chunk().chunkId(),
                        outcome.chunk().sourceFilePath(),
                        outcome.chunk().chunkIndex(),
                        outcome.status(),
                        outcome.reason()
                ))
                .toList();

        return new ProjectChunkEmbeddingPreview(
                result.projectId(),
                result.documentCount(),
                result.chunkCount(),
                result.embeddedCount(),
                result.failedCount(),
                result.embeddingModel(),
                result.dimensions(),
                result.status(),
                result.providerFailureReason(),
                result.chunkingDurationMillis(),
                result.embeddingDurationMillis(),
                result.averageEmbeddingMillisPerChunk(),
                result.totalDurationMillis(),
                chunks,
                failures
        );
    }

    public record ProjectChunkEmbeddingPreview(
            String projectId,
            long documentCount,
            long chunkCount,
            long embeddedCount,
            long failedCount,
            String embeddingModel,
            int dimensions,
            EmbeddingRunStatus status,
            String providerFailureReason,
            long chunkingDurationMillis,
            long embeddingDurationMillis,
            double averageEmbeddingMillisPerChunk,
            long totalDurationMillis,
            List<EmbeddedChunkPreview> chunks,
            List<ChunkEmbeddingFailurePreview> failures
    ) {
    }

    public record EmbeddedChunkPreview(
            String chunkId,
            String sourceFilePath,
            int chunkIndex,
            int dimensions,
            List<Float> vectorPreview
    ) {
    }

    public record ChunkEmbeddingFailurePreview(
            String chunkId,
            String sourceFilePath,
            int chunkIndex,
            ChunkEmbeddingStatus status,
            String reason
    ) {
    }
}
