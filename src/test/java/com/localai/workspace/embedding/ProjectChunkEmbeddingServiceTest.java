package com.localai.workspace.embedding;

import com.localai.workspace.chunk.DocumentChunk;
import com.localai.workspace.chunk.ProjectChunkingResult;
import com.localai.workspace.chunk.ProjectChunkingService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectChunkEmbeddingServiceTest {

    @Test
    void embedsChunksInOneBatchAndDerivesDimension() {
        ProjectChunkingService chunkingService = mock(ProjectChunkingService.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        List<DocumentChunk> chunks = List.of(chunk(0, "first"), chunk(1, "second"));
        when(chunkingService.chunk("Local_Ai_Work")).thenReturn(chunkingResult(chunks));
        when(embeddingService.embedAll(List.of("first", "second")))
                .thenReturn(List.of(new float[4], new float[4]));

        ProjectChunkEmbeddingResult result = service(chunkingService, embeddingService)
                .embed("Local_Ai_Work");

        assertThat(result.status()).isEqualTo(EmbeddingRunStatus.SUCCESS);
        assertThat(result.chunkCount()).isEqualTo(2);
        assertThat(result.embeddedCount()).isEqualTo(2);
        assertThat(result.failedCount()).isZero();
        assertThat(result.dimensions()).isEqualTo(4);
        assertThat(result.embeddingModel()).isEqualTo("qwen3-embedding:0.6b");
        assertThat(result.embeddingDurationMillis()).isNotNegative();
        assertThat(result.averageEmbeddingMillisPerChunk()).isNotNegative();
        assertThat(result.outcomes())
                .allMatch(outcome -> outcome.status() == ChunkEmbeddingStatus.EMBEDDED);
        verify(embeddingService).embedAll(List.of("first", "second"));
        verify(embeddingService, never()).embedVector("first");
    }

    @Test
    void marksOnlyTheInconsistentDimensionAsFailed() {
        ProjectChunkingService chunkingService = mock(ProjectChunkingService.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        List<DocumentChunk> chunks = List.of(
                chunk(0, "first"),
                chunk(1, "second"),
                chunk(2, "third")
        );
        when(chunkingService.chunk("Local_Ai_Work")).thenReturn(chunkingResult(chunks));
        when(embeddingService.embedAll(List.of("first", "second", "third")))
                .thenReturn(List.of(new float[4], new float[4], new float[3]));

        ProjectChunkEmbeddingResult result = service(chunkingService, embeddingService)
                .embed("Local_Ai_Work");

        assertThat(result.status()).isEqualTo(EmbeddingRunStatus.PARTIAL_FAILURE);
        assertThat(result.dimensions()).isEqualTo(4);
        assertThat(result.embeddedCount()).isEqualTo(2);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.outcomes())
                .filteredOn(outcome -> outcome.status() == ChunkEmbeddingStatus.DIMENSION_MISMATCH)
                .singleElement()
                .satisfies(outcome -> assertThat(outcome.reason())
                        .contains("Expected dimension 4")
                        .contains("received 3"));
    }

    @Test
    void fallsBackToIndividualRequestsForNonSystemBatchFailure() {
        ProjectChunkingService chunkingService = mock(ProjectChunkingService.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        List<DocumentChunk> chunks = List.of(chunk(0, "first"), chunk(1, "bad-data"));
        when(chunkingService.chunk("Local_Ai_Work")).thenReturn(chunkingResult(chunks));
        when(embeddingService.embedAll(List.of("first", "bad-data")))
                .thenThrow(new IllegalArgumentException("batch rejected"));
        when(embeddingService.embedVector("first")).thenReturn(new float[4]);
        when(embeddingService.embedVector("bad-data"))
                .thenThrow(new IllegalArgumentException("invalid chunk"));

        ProjectChunkEmbeddingResult result = service(chunkingService, embeddingService)
                .embed("Local_Ai_Work");

        assertThat(result.status()).isEqualTo(EmbeddingRunStatus.PARTIAL_FAILURE);
        assertThat(result.embeddedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.outcomes())
                .filteredOn(outcome -> outcome.status() == ChunkEmbeddingStatus.EMBEDDING_FAILED)
                .singleElement()
                .satisfies(outcome -> assertThat(outcome.reason()).contains("invalid chunk"));
    }

    @Test
    void reportsProviderConnectionFailureWithoutRetryingEveryChunk() {
        ProjectChunkingService chunkingService = mock(ProjectChunkingService.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        List<DocumentChunk> chunks = List.of(chunk(0, "first"), chunk(1, "second"));
        when(chunkingService.chunk("Local_Ai_Work")).thenReturn(chunkingResult(chunks));
        when(embeddingService.embedAll(List.of("first", "second")))
                .thenThrow(new IllegalStateException("Connection refused by Ollama"));

        ProjectChunkEmbeddingResult result = service(chunkingService, embeddingService)
                .embed("Local_Ai_Work");

        assertThat(result.status()).isEqualTo(EmbeddingRunStatus.PROVIDER_UNAVAILABLE);
        assertThat(result.embeddedCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(2);
        assertThat(result.providerFailureReason()).contains("Connection refused");
        assertThat(result.outcomes())
                .allMatch(outcome -> outcome.status() == ChunkEmbeddingStatus.PROVIDER_UNAVAILABLE);
        verify(embeddingService, never()).embedVector("first");
        verify(embeddingService, never()).embedVector("second");
    }

    @Test
    void reportsMissingModelSeparately() {
        ProjectChunkingService chunkingService = mock(ProjectChunkingService.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        List<DocumentChunk> chunks = List.of(chunk(0, "first"));
        when(chunkingService.chunk("Local_Ai_Work")).thenReturn(chunkingResult(chunks));
        when(embeddingService.embedAll(List.of("first")))
                .thenThrow(new IllegalStateException("model qwen3-embedding:0.6b not found"));

        ProjectChunkEmbeddingResult result = service(chunkingService, embeddingService)
                .embed("Local_Ai_Work");

        assertThat(result.status()).isEqualTo(EmbeddingRunStatus.MODEL_UNAVAILABLE);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.outcomes()).singleElement()
                .extracting(ChunkEmbeddingOutcome::status)
                .isEqualTo(ChunkEmbeddingStatus.MODEL_UNAVAILABLE);
    }

    @Test
    void reportsInvalidProviderVectorCountSeparately() {
        ProjectChunkingService chunkingService = mock(ProjectChunkingService.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        List<DocumentChunk> chunks = List.of(chunk(0, "first"), chunk(1, "second"));
        when(chunkingService.chunk("Local_Ai_Work")).thenReturn(chunkingResult(chunks));
        when(embeddingService.embedAll(List.of("first", "second")))
                .thenReturn(List.of(new float[4]));

        ProjectChunkEmbeddingResult result = service(chunkingService, embeddingService)
                .embed("Local_Ai_Work");

        assertThat(result.status()).isEqualTo(EmbeddingRunStatus.PROVIDER_FAILED);
        assertThat(result.embeddedCount()).isZero();
        assertThat(result.failedCount()).isEqualTo(2);
        assertThat(result.providerFailureReason())
                .contains("returned 1 vectors for 2 Chunks");
        assertThat(result.outcomes())
                .allMatch(outcome -> outcome.status() == ChunkEmbeddingStatus.PROVIDER_FAILED);
    }
    @Test
    void skipsAnUnexpectedEmptyChunkBeforeProviderCall() {
        ProjectChunkingService chunkingService = mock(ProjectChunkingService.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        List<DocumentChunk> chunks = List.of(chunk(0, ""));
        when(chunkingService.chunk("Local_Ai_Work")).thenReturn(chunkingResult(chunks));

        ProjectChunkEmbeddingResult result = service(chunkingService, embeddingService)
                .embed("Local_Ai_Work");

        assertThat(result.status()).isEqualTo(EmbeddingRunStatus.FAILED);
        assertThat(result.failedCount()).isEqualTo(1);
        assertThat(result.outcomes()).singleElement()
                .extracting(ChunkEmbeddingOutcome::status)
                .isEqualTo(ChunkEmbeddingStatus.EMPTY_CHUNK);
        verify(embeddingService, never()).embedAll(org.mockito.ArgumentMatchers.anyList());
    }

    private ProjectChunkEmbeddingService service(
            ProjectChunkingService chunkingService,
            EmbeddingService embeddingService
    ) {
        return new ProjectChunkEmbeddingService(
                chunkingService,
                embeddingService,
                "qwen3-embedding:0.6b"
        );
    }

    private ProjectChunkingResult chunkingResult(List<DocumentChunk> chunks) {
        return new ProjectChunkingResult(
                "Local_Ai_Work",
                "Local_Ai_Work",
                2,
                2,
                0,
                0,
                0,
                0,
                chunks.size(),
                100.0,
                5,
                200,
                10,
                chunks,
                List.of()
        );
    }

    private DocumentChunk chunk(int index, String content) {
        return new DocumentChunk(
                "chunk-" + index,
                "Local_Ai_Work",
                "README.md",
                "README.md",
                "md",
                index,
                content,
                index * 10,
                index * 10 + content.length(),
                1,
                1,
                "fingerprint",
                Instant.parse("2026-09-04T00:00:00Z")
        );
    }
}
