package com.localai.workspace.embedding;

import com.localai.workspace.chunk.DocumentChunk;
import com.localai.workspace.chunk.ProjectChunkingResult;
import com.localai.workspace.chunk.ProjectChunkingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProjectChunkEmbeddingService {

    private final ProjectChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final String embeddingModel;

    public ProjectChunkEmbeddingService(
            ProjectChunkingService chunkingService,
            EmbeddingService embeddingService,
            @Value("${spring.ai.ollama.embedding.model}") String embeddingModel
    ) {
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.embeddingModel = embeddingModel;
    }

    public ProjectChunkEmbeddingResult embed(String projectId) {
        long totalStartedAt = System.nanoTime();
        long chunkingStartedAt = System.nanoTime();
        ProjectChunkingResult chunkingResult = chunkingService.chunk(projectId);
        long chunkingDurationMillis = elapsedMillis(chunkingStartedAt);

        List<ChunkEmbeddingOutcome> outcomes = new ArrayList<>();
        List<DocumentChunk> candidates = new ArrayList<>();
        for (DocumentChunk chunk : chunkingResult.chunks()) {
            if (chunk.content() == null || chunk.content().isBlank()) {
                outcomes.add(ChunkEmbeddingOutcome.failed(
                        chunk,
                        ChunkEmbeddingStatus.EMPTY_CHUNK,
                        "Empty Chunk content was not sent to the Embedding provider"
                ));
            } else {
                candidates.add(chunk);
            }
        }

        long embeddingStartedAt = System.nanoTime();
        String providerFailureReason = null;
        if (!candidates.isEmpty()) {
            try {
                List<float[]> vectors = embeddingService.embedAll(
                        candidates.stream().map(DocumentChunk::content).toList()
                );
                if (vectors.size() != candidates.size()) {
                    providerFailureReason = "Embedding provider returned "
                            + vectors.size() + " vectors for " + candidates.size() + " Chunks";
                    addFailures(outcomes, candidates, ChunkEmbeddingStatus.PROVIDER_FAILED, providerFailureReason);
                } else {
                    outcomes.addAll(toOutcomes(candidates, vectors, List.of()));
                }
            } catch (RuntimeException batchFailure) {
                ChunkEmbeddingStatus systemStatus = classifySystemFailure(batchFailure);
                if (systemStatus != null) {
                    providerFailureReason = conciseReason(batchFailure);
                    addFailures(outcomes, candidates, systemStatus, providerFailureReason);
                } else {
                    outcomes.addAll(embedIndividually(candidates));
                }
            }
        }
        long embeddingDurationMillis = elapsedMillis(embeddingStartedAt);

        long embeddedCount = outcomes.stream()
                .filter(outcome -> outcome.status() == ChunkEmbeddingStatus.EMBEDDED)
                .count();
        long failedCount = outcomes.size() - embeddedCount;
        int dimensions = mostFrequentDimension(outcomes);
        EmbeddingRunStatus runStatus = runStatus(outcomes, embeddedCount, failedCount);
        if (providerFailureReason == null
                && (runStatus == EmbeddingRunStatus.PROVIDER_UNAVAILABLE
                || runStatus == EmbeddingRunStatus.MODEL_UNAVAILABLE
                || runStatus == EmbeddingRunStatus.PROVIDER_FAILED)) {
            providerFailureReason = outcomes.stream()
                    .map(ChunkEmbeddingOutcome::reason)
                    .filter(reason -> reason != null)
                    .findFirst()
                    .orElse(null);
        }

        return new ProjectChunkEmbeddingResult(
                chunkingResult.projectId(),
                chunkingResult.documentCount(),
                chunkingResult.chunkCount(),
                embeddedCount,
                failedCount,
                chunkingResult.sourceReadFailedCount(),
                chunkingResult.failedDocumentCount(),
                embeddingModel,
                dimensions,
                runStatus,
                providerFailureReason,
                chunkingDurationMillis,
                embeddingDurationMillis,
                candidates.isEmpty() ? 0.0 : (double) embeddingDurationMillis / candidates.size(),
                elapsedMillis(totalStartedAt),
                List.copyOf(outcomes)
        );
    }

    private List<ChunkEmbeddingOutcome> embedIndividually(List<DocumentChunk> chunks) {
        List<float[]> vectors = new ArrayList<>();
        List<ChunkEmbeddingOutcome> failures = new ArrayList<>();
        List<DocumentChunk> successfulChunks = new ArrayList<>();

        for (DocumentChunk chunk : chunks) {
            try {
                vectors.add(embeddingService.embedVector(chunk.content()));
                successfulChunks.add(chunk);
            } catch (RuntimeException exception) {
                ChunkEmbeddingStatus status = classifySystemFailure(exception);
                failures.add(ChunkEmbeddingOutcome.failed(
                        chunk,
                        status == null ? ChunkEmbeddingStatus.EMBEDDING_FAILED : status,
                        conciseReason(exception)
                ));
            }
        }

        return toOutcomes(successfulChunks, vectors, failures);
    }

    private List<ChunkEmbeddingOutcome> toOutcomes(
            List<DocumentChunk> chunks,
            List<float[]> vectors,
            List<ChunkEmbeddingOutcome> existingFailures
    ) {
        List<ChunkEmbeddingOutcome> outcomes = new ArrayList<>(existingFailures);
        int expectedDimension = mostFrequentDimension(vectors);

        for (int index = 0; index < chunks.size(); index++) {
            DocumentChunk chunk = chunks.get(index);
            float[] vector = vectors.get(index);
            if (vector == null || vector.length == 0) {
                outcomes.add(ChunkEmbeddingOutcome.failed(
                        chunk,
                        ChunkEmbeddingStatus.EMBEDDING_FAILED,
                        "Embedding provider returned an empty vector"
                ));
            } else if (vector.length != expectedDimension) {
                outcomes.add(ChunkEmbeddingOutcome.failed(
                        chunk,
                        ChunkEmbeddingStatus.DIMENSION_MISMATCH,
                        "Expected dimension " + expectedDimension + " but received " + vector.length
                ));
            } else {
                outcomes.add(ChunkEmbeddingOutcome.success(new EmbeddedChunk(
                        chunk,
                        embeddingModel,
                        vector.length,
                        vector
                )));
            }
        }
        return outcomes;
    }

    private void addFailures(
            List<ChunkEmbeddingOutcome> outcomes,
            List<DocumentChunk> chunks,
            ChunkEmbeddingStatus status,
            String reason
    ) {
        chunks.forEach(chunk -> outcomes.add(ChunkEmbeddingOutcome.failed(chunk, status, reason)));
    }

    private int mostFrequentDimension(List<float[]> vectors) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (float[] vector : vectors) {
            if (vector != null && vector.length > 0) {
                counts.merge(vector.length, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse(0);
    }

    private int mostFrequentDimension(Iterable<ChunkEmbeddingOutcome> outcomes) {
        List<float[]> vectors = new ArrayList<>();
        for (ChunkEmbeddingOutcome outcome : outcomes) {
            if (outcome.embeddedChunk() != null) {
                vectors.add(outcome.embeddedChunk().vector());
            }
        }
        return mostFrequentDimension(vectors);
    }

    private EmbeddingRunStatus runStatus(
            List<ChunkEmbeddingOutcome> outcomes,
            long embeddedCount,
            long failedCount
    ) {
        if (failedCount == 0) {
            return EmbeddingRunStatus.SUCCESS;
        }
        if (embeddedCount > 0) {
            return EmbeddingRunStatus.PARTIAL_FAILURE;
        }
        if (outcomes.stream().anyMatch(
                outcome -> outcome.status() == ChunkEmbeddingStatus.MODEL_UNAVAILABLE)) {
            return EmbeddingRunStatus.MODEL_UNAVAILABLE;
        }
        if (outcomes.stream().anyMatch(
                outcome -> outcome.status() == ChunkEmbeddingStatus.PROVIDER_UNAVAILABLE)) {
            return EmbeddingRunStatus.PROVIDER_UNAVAILABLE;
        }
        if (outcomes.stream().anyMatch(
                outcome -> outcome.status() == ChunkEmbeddingStatus.PROVIDER_FAILED)) {
            return EmbeddingRunStatus.PROVIDER_FAILED;
        }
        return EmbeddingRunStatus.FAILED;
    }

    private ChunkEmbeddingStatus classifySystemFailure(RuntimeException exception) {
        String type = exception.getClass().getName().toLowerCase(Locale.ROOT);
        String message = allMessages(exception).toLowerCase(Locale.ROOT);
        if ((message.contains("model") && message.contains("not found"))
                || message.contains("try pulling it first")
                || message.contains("pull model")) {
            return ChunkEmbeddingStatus.MODEL_UNAVAILABLE;
        }
        if (type.contains("resourceaccessexception")
                || message.contains("connection refused")
                || message.contains("failed to connect")
                || message.contains("connect timed out")
                || message.contains("connection reset")
                || message.contains("unreachable")) {
            return ChunkEmbeddingStatus.PROVIDER_UNAVAILABLE;
        }
        return null;
    }

    private String conciseReason(Throwable throwable) {
        String message = allMessages(throwable);
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private String allMessages(Throwable throwable) {
        List<String> messages = new ArrayList<>();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                messages.add(current.getClass().getSimpleName() + ": " + current.getMessage());
            }
            current = current.getCause();
        }
        return messages.isEmpty() ? throwable.getClass().getSimpleName() : String.join(" -> ", messages);
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
