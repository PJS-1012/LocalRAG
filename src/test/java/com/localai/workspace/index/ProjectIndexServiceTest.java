package com.localai.workspace.index;

import com.localai.workspace.chunk.DocumentChunk;
import com.localai.workspace.embedding.ChunkEmbeddingOutcome;
import com.localai.workspace.embedding.ChunkEmbeddingStatus;
import com.localai.workspace.embedding.EmbeddedChunk;
import com.localai.workspace.embedding.EmbeddingRunStatus;
import com.localai.workspace.embedding.ProjectChunkEmbeddingResult;
import com.localai.workspace.embedding.ProjectChunkEmbeddingService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectIndexServiceTest {

    @Test
    void storesOnlyACompleteDimensionCompatibleEmbeddingRun() {
        ProjectChunkEmbeddingService embeddingService = mock(ProjectChunkEmbeddingService.class);
        ProjectIndexRepository repository = mock(ProjectIndexRepository.class);
        EmbeddedChunk embedded = embedded(1024);
        when(embeddingService.embed("Local_Ai_Work"))
                .thenReturn(successResult(embedded));
        when(repository.synchronize("Local_Ai_Work", List.of(embedded)))
                .thenReturn(new ProjectIndexWriteResult(1, 2, 1, 5));

        ProjectIndexResult result = service(embeddingService, repository).index("Local_Ai_Work");

        assertThat(result.status()).isEqualTo(ProjectIndexStatus.SUCCESS);
        assertThat(result.writtenCount()).isEqualTo(1);
        assertThat(result.deletedCount()).isEqualTo(2);
        assertThat(result.storedCount()).isEqualTo(1);
        assertThat(result.databaseWriteDurationMillis()).isEqualTo(5);
        verify(repository).synchronize("Local_Ai_Work", List.of(embedded));
    }

    @Test
    void doesNotWriteWhenProviderOrChunkEmbeddingFailed() {
        ProjectChunkEmbeddingService embeddingService = mock(ProjectChunkEmbeddingService.class);
        ProjectIndexRepository repository = mock(ProjectIndexRepository.class);
        DocumentChunk chunk = chunk();
        when(embeddingService.embed("Local_Ai_Work")).thenReturn(new ProjectChunkEmbeddingResult(
                "Local_Ai_Work",
                1,
                1,
                0,
                1,
                0,
                0,
                "qwen3-embedding:0.6b",
                0,
                EmbeddingRunStatus.PROVIDER_UNAVAILABLE,
                "Connection refused",
                10,
                2,
                2.0,
                12,
                List.of(ChunkEmbeddingOutcome.failed(
                        chunk,
                        ChunkEmbeddingStatus.PROVIDER_UNAVAILABLE,
                        "Connection refused"
                ))
        ));
        when(repository.count("Local_Ai_Work")).thenReturn(3L);

        ProjectIndexResult result = service(embeddingService, repository).index("Local_Ai_Work");

        assertThat(result.status()).isEqualTo(ProjectIndexStatus.EMBEDDING_FAILED);
        assertThat(result.reason()).contains("Connection refused");
        assertThat(result.storedCount()).isEqualTo(3);
        verify(repository, never()).synchronize(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    @Test
    void rejectsDimensionBeforeDatabaseWrite() {
        ProjectChunkEmbeddingService embeddingService = mock(ProjectChunkEmbeddingService.class);
        ProjectIndexRepository repository = mock(ProjectIndexRepository.class);
        EmbeddedChunk embedded = embedded(768);
        when(embeddingService.embed("Local_Ai_Work"))
                .thenReturn(successResult(embedded));
        when(repository.count("Local_Ai_Work")).thenReturn(0L);

        ProjectIndexResult result = service(embeddingService, repository).index("Local_Ai_Work");

        assertThat(result.status()).isEqualTo(ProjectIndexStatus.DIMENSION_MISMATCH);
        assertThat(result.reason()).contains("expects dimension 1024").contains("returned 768");
        verify(repository, never()).synchronize(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    @Test
    void reportsStorageFailureWithoutLeakingAPartialSuccess() {
        ProjectChunkEmbeddingService embeddingService = mock(ProjectChunkEmbeddingService.class);
        ProjectIndexRepository repository = mock(ProjectIndexRepository.class);
        EmbeddedChunk embedded = embedded(1024);
        when(embeddingService.embed("Local_Ai_Work"))
                .thenReturn(successResult(embedded));
        when(repository.synchronize("Local_Ai_Work", List.of(embedded)))
                .thenThrow(new IllegalStateException("write failed"));
        when(repository.count("Local_Ai_Work")).thenReturn(4L);

        ProjectIndexResult result = service(embeddingService, repository).index("Local_Ai_Work");

        assertThat(result.status()).isEqualTo(ProjectIndexStatus.STORAGE_FAILED);
        assertThat(result.reason()).contains("write failed");
        assertThat(result.writtenCount()).isZero();
        assertThat(result.deletedCount()).isZero();
        assertThat(result.storedCount()).isEqualTo(4);
    }

    private ProjectIndexService service(
            ProjectChunkEmbeddingService embeddingService,
            ProjectIndexRepository repository
    ) {
        return new ProjectIndexService(
                embeddingService,
                repository,
                new IndexingProperties(1024)
        );
    }

    private ProjectChunkEmbeddingResult successResult(EmbeddedChunk embedded) {
        return new ProjectChunkEmbeddingResult(
                "Local_Ai_Work",
                1,
                1,
                1,
                0,
                0,
                0,
                "qwen3-embedding:0.6b",
                embedded.dimensions(),
                EmbeddingRunStatus.SUCCESS,
                null,
                10,
                20,
                20.0,
                30,
                List.of(ChunkEmbeddingOutcome.success(embedded))
        );
    }

    private EmbeddedChunk embedded(int dimensions) {
        return new EmbeddedChunk(
                chunk(),
                "qwen3-embedding:0.6b",
                dimensions,
                new float[dimensions]
        );
    }

    private DocumentChunk chunk() {
        return new DocumentChunk(
                "a".repeat(64),
                "Local_Ai_Work",
                "README.md",
                "README.md",
                "md",
                0,
                "# LocalRAG",
                0,
                10,
                1,
                1,
                "f".repeat(64),
                Instant.parse("2026-09-04T00:00:00Z")
        );
    }
}
