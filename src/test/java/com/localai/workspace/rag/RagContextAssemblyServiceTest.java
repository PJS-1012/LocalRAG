package com.localai.workspace.rag;

import com.localai.workspace.search.ProjectSemanticSearchMatch;
import com.localai.workspace.search.ProjectSemanticSearchResponse;
import com.localai.workspace.search.ProjectSemanticSearchService;
import com.localai.workspace.search.ProjectSemanticSearchStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagContextAssemblyServiceTest {

    @Test
    void assemblesRankedSourcesWithinConfiguredBudget() {
        ProjectSemanticSearchService searchService = mock(ProjectSemanticSearchService.class);
        when(searchService.search(argThat(request ->
                request.projectId().equals("Local_Ai_Work")
                        && request.query().equals("project discovery")
                        && request.topK() == null
                        && request.threshold() == null
                        && request.instructionEnabled() == null
        ))).thenReturn(success(List.of(
                match(2, "low", "docs/README.md", 0.62, "lower relevance"),
                match(1, "high", "src/Discovery.java", 0.91, "class Discovery {}")
        )));

        RagContextAssemblyResult result = service(searchService, 8000).assemble(
                new RagContextPreviewRequest("Local_Ai_Work", "project discovery")
        );

        assertThat(result.status()).isEqualTo(RagContextAssemblyStatus.SUCCESS);
        assertThat(result.contextBudgetCharacters()).isEqualTo(8000);
        assertThat(result.sourceCount()).isEqualTo(2);
        assertThat(result.includedChunkCount()).isEqualTo(2);
        assertThat(result.excludedByBudgetCount()).isZero();
        assertThat(result.totalContextCharacters()).isEqualTo(result.context().length());
        assertThat(result.sources()).extracting(RagContextSource::chunkId)
                .containsExactly("high", "low");
        assertThat(result.sources()).extracting(RagContextSource::citationId)
                .containsExactly("S1", "S2");
        assertThat(result.sources().get(0).similarity()).isEqualTo(0.91);
        assertThat(result.context())
                .contains("[Source S1]")
                .contains("File: src/Discovery.java")
                .contains("Lines: 10-20")
                .contains("class Discovery {}")
                .doesNotContain("Similarity")
                .doesNotContain("0.91");
        assertThat(result.context().indexOf("[Source S1]"))
                .isLessThan(result.context().indexOf("[Source S2]"));
        verify(searchService).search(argThat(request -> request.instructionEnabled() == null));
    }

    @Test
    void excludesWholeChunkInsteadOfTruncatingIt() {
        ProjectSemanticSearchService searchService = mock(ProjectSemanticSearchService.class);
        when(searchService.search(org.mockito.ArgumentMatchers.any())).thenReturn(success(List.of(
                match(1, "small", "A.java", 0.90, "short content"),
                match(2, "large", "B.java", 0.80, "x".repeat(500))
        )));

        RagContextAssemblyResult result = service(searchService, 220).assemble(
                new RagContextPreviewRequest("Local_Ai_Work", "query")
        );

        assertThat(result.sourceCount()).isEqualTo(1);
        assertThat(result.excludedByBudgetCount()).isEqualTo(1);
        assertThat(result.totalContextCharacters()).isLessThanOrEqualTo(220);
        assertThat(result.context()).contains("short content").doesNotContain("x".repeat(20));
        assertThat(result.sources()).extracting(RagContextSource::chunkId)
                .containsExactly("small");
    }

    @Test
    void returnsEmptyContextWhenSearchHasNoMatches() {
        ProjectSemanticSearchService searchService = mock(ProjectSemanticSearchService.class);
        when(searchService.search(org.mockito.ArgumentMatchers.any())).thenReturn(success(List.of()));

        RagContextAssemblyResult result = service(searchService, 8000).assemble(
                new RagContextPreviewRequest("Local_Ai_Work", "Kafka consumer")
        );

        assertThat(result.status()).isEqualTo(RagContextAssemblyStatus.SUCCESS);
        assertThat(result.sourceCount()).isZero();
        assertThat(result.excludedByBudgetCount()).isZero();
        assertThat(result.context()).isEmpty();
    }

    @Test
    void rejectsAnyCrossProjectSearchResult() {
        ProjectSemanticSearchService searchService = mock(ProjectSemanticSearchService.class);
        when(searchService.search(org.mockito.ArgumentMatchers.any())).thenReturn(success(List.of(
                new ProjectSemanticSearchMatch(
                        1, "foreign", "Other_Project", "Secret.java", "Secret.java",
                        "java", 0, 1, 3, "secret", 0.99, "model"
                )
        )));

        RagContextAssemblyResult result = service(searchService, 8000).assemble(
                new RagContextPreviewRequest("Local_Ai_Work", "query")
        );

        assertThat(result.status()).isEqualTo(RagContextAssemblyStatus.SCOPE_VIOLATION);
        assertThat(result.context()).isEmpty();
        assertThat(result.sources()).isEmpty();
    }

    @Test
    void isolatesSearchFailureAsEmptyContext() {
        ProjectSemanticSearchService searchService = mock(ProjectSemanticSearchService.class);
        ProjectSemanticSearchResponse failed = new ProjectSemanticSearchResponse(
                "Local_Ai_Work", "query", 5, 0.45, true, 0, 0,
                0, 0, 0, ProjectSemanticSearchStatus.INDEX_NOT_FOUND,
                "No index", List.of()
        );
        when(searchService.search(org.mockito.ArgumentMatchers.any())).thenReturn(failed);

        RagContextAssemblyResult result = service(searchService, 8000).assemble(
                new RagContextPreviewRequest("Local_Ai_Work", "query")
        );

        assertThat(result.status()).isEqualTo(RagContextAssemblyStatus.SEARCH_FAILED);
        assertThat(result.searchStatus()).isEqualTo(ProjectSemanticSearchStatus.INDEX_NOT_FOUND);
        assertThat(result.reason()).isEqualTo("No index");
        assertThat(result.context()).isEmpty();
    }

    private RagContextAssemblyService service(ProjectSemanticSearchService searchService, int budget) {
        return new RagContextAssemblyService(searchService, new RagContextProperties(budget));
    }

    private ProjectSemanticSearchResponse success(List<ProjectSemanticSearchMatch> matches) {
        return new ProjectSemanticSearchResponse(
                "Local_Ai_Work", "query", 5, 0.45, true, 1024, matches.size(),
                10, 3, 13, ProjectSemanticSearchStatus.SUCCESS, null, matches
        );
    }

    private ProjectSemanticSearchMatch match(
            int rank,
            String chunkId,
            String path,
            double similarity,
            String content
    ) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return new ProjectSemanticSearchMatch(
                rank, chunkId, "Local_Ai_Work", path, fileName, "java",
                rank - 1, 10, 20, content, similarity, "qwen3-embedding:0.6b"
        );
    }
}
