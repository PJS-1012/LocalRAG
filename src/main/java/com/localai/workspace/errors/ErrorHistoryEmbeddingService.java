package com.localai.workspace.errors;

import com.localai.workspace.embedding.EmbeddingService;
import org.springframework.stereotype.Service;

@Service
public class ErrorHistoryEmbeddingService {
    private final EmbeddingService embeddings;
    private final ErrorHistoryEmbeddingRepository repository;
    private final ErrorSimilarityProperties properties;

    public ErrorHistoryEmbeddingService(EmbeddingService embeddings, ErrorHistoryEmbeddingRepository repository,
            ErrorSimilarityProperties properties) {
        this.embeddings = embeddings;
        this.repository = repository;
        this.properties = properties;
    }

    public ErrorEmbeddingIndexResult index(ErrorHistory history) {
        String source = ErrorHistoryEmbeddingText.from(history);
        String fingerprint = ErrorHistoryEmbeddingText.fingerprint(source);
        if (repository.storedIndex(history.id).filter(stored -> fingerprint.equals(stored.fingerprint())
                && properties.embeddingModel().equals(stored.model())
                && properties.expectedDimensions() == stored.dimensions()).isPresent()) {
            return new ErrorEmbeddingIndexResult(ErrorEmbeddingIndexResult.Status.UNCHANGED,
                    properties.expectedDimensions(), 0, 0, null);
        }
        long embeddingStarted = System.nanoTime();
        float[] vector;
        try {
            vector = embeddings.embedVector(source);
        } catch (RuntimeException exception) {
            return new ErrorEmbeddingIndexResult(ErrorEmbeddingIndexResult.Status.EMBEDDING_FAILED,
                    0, elapsed(embeddingStarted), 0, "Embedding provider unavailable");
        }
        long embeddingMillis = elapsed(embeddingStarted);
        if (vector == null || vector.length != properties.expectedDimensions()) {
            return new ErrorEmbeddingIndexResult(ErrorEmbeddingIndexResult.Status.EMBEDDING_FAILED,
                    vector == null ? 0 : vector.length, embeddingMillis, 0, "Unexpected embedding dimension");
        }
        long databaseStarted = System.nanoTime();
        repository.upsert(history, properties.embeddingModel(), vector.length, fingerprint, vector);
        return new ErrorEmbeddingIndexResult(ErrorEmbeddingIndexResult.Status.INDEXED, vector.length,
                embeddingMillis, elapsed(databaseStarted), null);
    }

    private long elapsed(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
