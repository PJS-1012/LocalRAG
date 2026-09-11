package com.localai.workspace.errors;

import com.localai.workspace.agent.LogSecretRedactor;
import com.localai.workspace.embedding.EmbeddingService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class ErrorSimilarityService {
    static final String CAUTION = "A similar past error does not prove the current error has the same cause; verify it separately.";
    private final ErrorProjectScope scope;
    private final LogSecretRedactor redactor;
    private final EmbeddingService embeddings;
    private final ErrorHistoryEmbeddingRepository repository;
    private final ErrorSimilarityProperties properties;

    public ErrorSimilarityService(ErrorProjectScope scope, LogSecretRedactor redactor,
            EmbeddingService embeddings, ErrorHistoryEmbeddingRepository repository,
            ErrorSimilarityProperties properties) {
        this.scope = scope;
        this.redactor = redactor;
        this.embeddings = embeddings;
        this.repository = repository;
        this.properties = properties;
    }

    public ErrorSimilarityResponse find(ErrorSimilarRequest request) {
        long started = System.nanoTime();
        String project = scope.require(request.projectId());
        int topK = request.topK() == null ? properties.defaultTopK() : request.topK();
        if (topK < 1 || topK > properties.maxTopK())
            throw new ResponseStatusException(BAD_REQUEST, "topK must be between 1 and " + properties.maxTopK());
        String source = ErrorHistoryEmbeddingText.from(redactor.redact(request.errorType()),
                redactor.redact(request.errorMessage()), redactor.redact(request.symptom()));
        long embeddingStarted = System.nanoTime();
        float[] vector = embeddings.embedVector(source);
        long embeddingMillis = elapsed(embeddingStarted);
        if (vector == null || vector.length != properties.expectedDimensions())
            throw new IllegalStateException("Unexpected embedding dimension");
        long searchStarted = System.nanoTime();
        List<ErrorSimilarResult> results = repository.search(project, vector, topK, properties.threshold()).stream()
                .map(this::toResult).toList();
        long searchMillis = elapsed(searchStarted);
        return new ErrorSimilarityResponse("SUCCESS", project, topK, properties.threshold(), results.size(),
                embeddingMillis, searchMillis, elapsed(started), CAUTION, results);
    }

    private ErrorSimilarResult toResult(ErrorSimilarMatch match) {
        boolean verified = match.status() != ErrorStatus.UNVERIFIED;
        return new ErrorSimilarResult(match.errorHistoryId(), match.status(), switch (match.status()) {
            case RESOLVED -> "PAST_RESOLVED_CASE";
            case VERIFIED -> "PAST_VERIFIED_ANALYSIS";
            case UNVERIFIED -> "PAST_UNVERIFIED_ANALYSIS";
        }, match.similarity(), match.errorType(), match.errorMessage(),
                verified ? match.rootCause() : null, verified ? match.solution() : null,
                match.occurredAt(), match.relatedFiles(), match.relatedCommits());
    }

    private long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
