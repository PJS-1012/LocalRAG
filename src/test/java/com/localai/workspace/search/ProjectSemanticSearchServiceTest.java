package com.localai.workspace.search;

import com.localai.workspace.embedding.EmbeddingService;
import com.localai.workspace.index.IndexingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectSemanticSearchServiceTest {

    @Test
    void embedsAndSearchesWithConfiguredDefaults() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        ProjectSemanticSearchRepository repository = mock(ProjectSemanticSearchRepository.class);
        float[] vector = new float[1024];
        when(repository.count("Local_Ai_Work")).thenReturn(10L);
        when(embeddingService.embedVector("project discovery")).thenReturn(vector);
        when(repository.search("Local_Ai_Work", vector, 5, 0.45)).thenReturn(List.of(match()));

        ProjectSemanticSearchResponse response = service(embeddingService, repository).search(
                new ProjectSemanticSearchRequest(
                        "Local_Ai_Work",
                        " project discovery ",
                        null,
                        null
                )
        );

        assertThat(response.status()).isEqualTo(ProjectSemanticSearchStatus.SUCCESS);
        assertThat(response.topK()).isEqualTo(5);
        assertThat(response.threshold()).isEqualTo(0.45);
        assertThat(response.queryEmbeddingDimension()).isEqualTo(1024);
        assertThat(response.resultCount()).isEqualTo(1);
        assertThat(response.results().get(0).rank()).isEqualTo(1);
        assertThat(response.results().get(0).filePath()).isEqualTo("Discovery.java");
        verify(embeddingService).embedVector("project discovery");
    }

    @Test
    void rejectsBlankQueryBeforeIndexOrProviderAccess() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        ProjectSemanticSearchRepository repository = mock(ProjectSemanticSearchRepository.class);

        ProjectSemanticSearchResponse response = service(embeddingService, repository).search(
                new ProjectSemanticSearchRequest("Local_Ai_Work", "  ", 5, 0.50)
        );

        assertThat(response.status()).isEqualTo(ProjectSemanticSearchStatus.INVALID_REQUEST);
        verify(repository, never()).count(org.mockito.ArgumentMatchers.anyString());
        verify(embeddingService, never()).embedVector(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void reportsMissingProjectIndexWithoutEmbeddingQuery() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        ProjectSemanticSearchRepository repository = mock(ProjectSemanticSearchRepository.class);
        when(repository.count("Missing")).thenReturn(0L);

        ProjectSemanticSearchResponse response = service(embeddingService, repository).search(
                new ProjectSemanticSearchRequest("Missing", "anything", 5, 0.50)
        );

        assertThat(response.status()).isEqualTo(ProjectSemanticSearchStatus.INDEX_NOT_FOUND);
        verify(embeddingService, never()).embedVector(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void distinguishesProviderUnavailable() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        ProjectSemanticSearchRepository repository = mock(ProjectSemanticSearchRepository.class);
        when(repository.count("Local_Ai_Work")).thenReturn(1L);
        when(embeddingService.embedVector("query"))
                .thenThrow(new RuntimeException("connection refused"));

        ProjectSemanticSearchResponse response = service(embeddingService, repository).search(
                new ProjectSemanticSearchRequest("Local_Ai_Work", "query", 5, 0.50)
        );

        assertThat(response.status()).isEqualTo(ProjectSemanticSearchStatus.PROVIDER_UNAVAILABLE);
        verify(repository, never()).search(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble()
        );
    }

    @Test
    void blocksDimensionMismatchBeforeDatabaseSearch() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        ProjectSemanticSearchRepository repository = mock(ProjectSemanticSearchRepository.class);
        when(repository.count("Local_Ai_Work")).thenReturn(1L);
        when(embeddingService.embedVector("query")).thenReturn(new float[768]);

        ProjectSemanticSearchResponse response = service(embeddingService, repository).search(
                new ProjectSemanticSearchRequest("Local_Ai_Work", "query", 5, 0.50)
        );

        assertThat(response.status()).isEqualTo(ProjectSemanticSearchStatus.DIMENSION_MISMATCH);
        assertThat(response.reason()).contains("1024").contains("768");
        verify(repository, never()).search(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble()
        );
    }

    @Test
    void reportsDatabaseFailureSeparately() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        ProjectSemanticSearchRepository repository = mock(ProjectSemanticSearchRepository.class);
        when(repository.count("Local_Ai_Work")).thenThrow(new RuntimeException("database down"));

        ProjectSemanticSearchResponse response = service(embeddingService, repository).search(
                new ProjectSemanticSearchRequest("Local_Ai_Work", "query", 5, 0.50)
        );

        assertThat(response.status()).isEqualTo(ProjectSemanticSearchStatus.DATABASE_FAILED);
        assertThat(response.reason()).contains("database down");
    }

    @Test
    void treatsNoThresholdMatchesAsSuccessfulEmptyResults() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        ProjectSemanticSearchRepository repository = mock(ProjectSemanticSearchRepository.class);
        float[] vector = new float[1024];
        when(repository.count("Local_Ai_Work")).thenReturn(1L);
        when(embeddingService.embedVector("unrelated")).thenReturn(vector);
        when(repository.search("Local_Ai_Work", vector, 5, 0.50)).thenReturn(List.of());

        ProjectSemanticSearchResponse response = service(embeddingService, repository).search(
                new ProjectSemanticSearchRequest("Local_Ai_Work", "unrelated", 5, 0.50)
        );

        assertThat(response.status()).isEqualTo(ProjectSemanticSearchStatus.SUCCESS);
        assertThat(response.resultCount()).isZero();
        assertThat(response.results()).isEmpty();
    }

    private ProjectSemanticSearchService service(
            EmbeddingService embeddingService,
            ProjectSemanticSearchRepository repository
    ) {
        return new ProjectSemanticSearchService(
                embeddingService,
                repository,
                new ProjectSearchProperties(5, 0.45, 20),
                new IndexingProperties(1024)
        );
    }

    private StoredChunkMatch match() {
        return new StoredChunkMatch(
                "chunk-id",
                "Local_Ai_Work",
                "Discovery.java",
                "Discovery.java",
                "java",
                0,
                10,
                20,
                "class Discovery {}",
                0.82,
                "qwen3-embedding:0.6b"
        );
    }
}
