package com.localai.workspace.search;

import com.localai.workspace.embedding.EmbeddingService;
import com.localai.workspace.index.IndexingProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

@Service
public class ProjectSemanticSearchService {

    private final EmbeddingService embeddingService;
    private final ProjectSemanticSearchRepository repository;
    private final ProjectSearchProperties searchProperties;
    private final IndexingProperties indexingProperties;

    public ProjectSemanticSearchService(
            EmbeddingService embeddingService,
            ProjectSemanticSearchRepository repository,
            ProjectSearchProperties searchProperties,
            IndexingProperties indexingProperties
    ) {
        this.embeddingService = embeddingService;
        this.repository = repository;
        this.searchProperties = searchProperties;
        this.indexingProperties = indexingProperties;
    }

    public ProjectSemanticSearchResponse search(ProjectSemanticSearchRequest request) {
        long startedAt = System.nanoTime();
        int topK = request == null || request.topK() == null
                ? searchProperties.defaultTopK()
                : request.topK();
        double threshold = request == null || request.threshold() == null
                ? searchProperties.defaultThreshold()
                : request.threshold();
        String projectId = request == null ? null : request.projectId();
        String query = request == null ? null : request.query();
        boolean instructionEnabled = request == null || request.instructionEnabled() == null
                ? searchProperties.queryInstruction().enabled()
                : request.instructionEnabled();

        String validationFailure = validate(projectId, query, topK, threshold);
        if (validationFailure != null) {
            return failure(
                    projectId,
                    query,
                    topK,
                    threshold,
                    instructionEnabled,
                    0,
                    0,
                    0,
                    ProjectSemanticSearchStatus.INVALID_REQUEST,
                    validationFailure,
                    startedAt
            );
        }

        try {
            if (repository.count(projectId) == 0) {
                return failure(
                        projectId,
                        query,
                        topK,
                        threshold,
                        instructionEnabled,
                        0,
                        0,
                        0,
                        ProjectSemanticSearchStatus.INDEX_NOT_FOUND,
                        "No stored Index exists for Project: " + projectId,
                        startedAt
                );
            }
        } catch (RuntimeException exception) {
            return failure(
                    projectId,
                    query,
                    topK,
                    threshold,
                    instructionEnabled,
                    0,
                    0,
                    0,
                    ProjectSemanticSearchStatus.DATABASE_FAILED,
                    conciseReason(exception),
                    startedAt
            );
        }

        long embeddingStartedAt = System.nanoTime();
        float[] queryVector;
        try {
            queryVector = embeddingService.embedVector(
                    queryEmbeddingInput(query, instructionEnabled));
        } catch (RuntimeException exception) {
            return failure(
                    projectId,
                    query,
                    topK,
                    threshold,
                    instructionEnabled,
                    0,
                    elapsedMillis(embeddingStartedAt),
                    0,
                    classifyProviderFailure(exception),
                    conciseReason(exception),
                    startedAt
            );
        }
        long embeddingDurationMillis = elapsedMillis(embeddingStartedAt);

        if (queryVector == null || queryVector.length != indexingProperties.expectedDimensions()) {
            int dimensions = queryVector == null ? 0 : queryVector.length;
            return failure(
                    projectId,
                    query,
                    topK,
                    threshold,
                    instructionEnabled,
                    dimensions,
                    embeddingDurationMillis,
                    0,
                    ProjectSemanticSearchStatus.DIMENSION_MISMATCH,
                    "DB schema expects dimension " + indexingProperties.expectedDimensions()
                            + " but Query Embedding returned " + dimensions,
                    startedAt
            );
        }

        long databaseStartedAt = System.nanoTime();
        try {
            List<StoredChunkMatch> storedMatches = repository.search(
                    projectId,
                    queryVector,
                    topK,
                    threshold
            );
            long databaseDurationMillis = elapsedMillis(databaseStartedAt);
            List<ProjectSemanticSearchMatch> matches = IntStream.range(0, storedMatches.size())
                    .mapToObj(index -> toMatch(index + 1, storedMatches.get(index)))
                    .toList();
            return new ProjectSemanticSearchResponse(
                    projectId,
                    query,
                    topK,
                    threshold,
                    instructionEnabled,
                    queryVector.length,
                    matches.size(),
                    embeddingDurationMillis,
                    databaseDurationMillis,
                    elapsedMillis(startedAt),
                    ProjectSemanticSearchStatus.SUCCESS,
                    null,
                    matches
            );
        } catch (RuntimeException exception) {
            return failure(
                    projectId,
                    query,
                    topK,
                    threshold,
                    instructionEnabled,
                    queryVector.length,
                    embeddingDurationMillis,
                    elapsedMillis(databaseStartedAt),
                    ProjectSemanticSearchStatus.DATABASE_FAILED,
                    conciseReason(exception),
                    startedAt
            );
        }
    }

    private String queryEmbeddingInput(String query, boolean instructionEnabled) {
        String normalizedQuery = query.strip();
        if (!instructionEnabled) {
            return normalizedQuery;
        }
        return searchProperties.queryInstruction().apply(normalizedQuery);
    }

    private String validate(String projectId, String query, int topK, double threshold) {
        if (projectId == null || projectId.isBlank()) {
            return "projectId must not be blank";
        }
        if (query == null || query.isBlank()) {
            return "query must not be blank";
        }
        if (topK < 1 || topK > searchProperties.maxTopK()) {
            return "topK must be between 1 and " + searchProperties.maxTopK();
        }
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            return "threshold must be between 0 and 1";
        }
        return null;
    }

    private ProjectSemanticSearchMatch toMatch(int rank, StoredChunkMatch match) {
        return new ProjectSemanticSearchMatch(
                rank,
                match.chunkId(),
                match.projectId(),
                match.sourcePath(),
                match.sourceFileName(),
                match.extension(),
                match.chunkIndex(),
                match.startLine(),
                match.endLine(),
                match.content(),
                match.similarity(),
                match.embeddingModel()
        );
    }

    private ProjectSemanticSearchResponse failure(
            String projectId,
            String query,
            int topK,
            double threshold,
            boolean instructionEnabled,
            int dimensions,
            long embeddingDurationMillis,
            long databaseDurationMillis,
            ProjectSemanticSearchStatus status,
            String reason,
            long startedAt
    ) {
        return new ProjectSemanticSearchResponse(
                projectId,
                query,
                topK,
                threshold,
                instructionEnabled,
                dimensions,
                0,
                embeddingDurationMillis,
                databaseDurationMillis,
                elapsedMillis(startedAt),
                status,
                reason,
                List.of()
        );
    }

    private ProjectSemanticSearchStatus classifyProviderFailure(RuntimeException exception) {
        String type = exception.getClass().getName().toLowerCase(Locale.ROOT);
        String message = allMessages(exception).toLowerCase(Locale.ROOT);
        if ((message.contains("model") && message.contains("not found"))
                || message.contains("try pulling it first")
                || message.contains("pull model")) {
            return ProjectSemanticSearchStatus.MODEL_UNAVAILABLE;
        }
        if (type.contains("resourceaccessexception")
                || message.contains("connection refused")
                || message.contains("failed to connect")
                || message.contains("connect timed out")
                || message.contains("connection reset")
                || message.contains("unreachable")) {
            return ProjectSemanticSearchStatus.PROVIDER_UNAVAILABLE;
        }
        return ProjectSemanticSearchStatus.PROVIDER_FAILED;
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
