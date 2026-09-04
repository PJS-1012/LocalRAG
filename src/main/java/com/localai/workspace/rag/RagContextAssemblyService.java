package com.localai.workspace.rag;

import com.localai.workspace.search.ProjectSemanticSearchMatch;
import com.localai.workspace.search.ProjectSemanticSearchRequest;
import com.localai.workspace.search.ProjectSemanticSearchResponse;
import com.localai.workspace.search.ProjectSemanticSearchService;
import com.localai.workspace.search.ProjectSemanticSearchStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RagContextAssemblyService {

    private static final String SOURCE_SEPARATOR = "\n\n";

    private final ProjectSemanticSearchService searchService;
    private final RagContextProperties properties;

    public RagContextAssemblyService(
            ProjectSemanticSearchService searchService,
            RagContextProperties properties
    ) {
        this.searchService = searchService;
        this.properties = properties;
    }

    public RagContextAssemblyResult assemble(RagContextPreviewRequest request) {
        ProjectSemanticSearchResponse searchResponse = searchService.search(
                new ProjectSemanticSearchRequest(
                        request == null ? null : request.projectId(),
                        request == null ? null : request.query(),
                        null,
                        null,
                        null
                )
        );

        if (searchResponse.status() != ProjectSemanticSearchStatus.SUCCESS) {
            return result(
                    searchResponse,
                    RagContextAssemblyStatus.SEARCH_FAILED,
                    searchResponse.reason(),
                    List.of(),
                    0,
                    ""
            );
        }

        List<ProjectSemanticSearchMatch> rankedMatches = searchResponse.results().stream()
                .sorted(Comparator.comparingDouble(ProjectSemanticSearchMatch::similarity)
                        .reversed()
                        .thenComparingInt(ProjectSemanticSearchMatch::rank))
                .toList();

        if (rankedMatches.stream().anyMatch(
                match -> !searchResponse.projectId().equals(match.projectId()))) {
            return result(
                    searchResponse,
                    RagContextAssemblyStatus.SCOPE_VIOLATION,
                    "Search result contained a Chunk outside Project: "
                            + searchResponse.projectId(),
                    List.of(),
                    0,
                    ""
            );
        }

        List<RagContextSource> sources = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        int excludedByBudget = 0;

        for (ProjectSemanticSearchMatch match : rankedMatches) {
            String citationId = "S" + (sources.size() + 1);
            RagContextSource source = toSource(citationId, match);
            String formattedSource = format(source);
            int separatorLength = context.isEmpty() ? 0 : SOURCE_SEPARATOR.length();

            if (context.length() + separatorLength + formattedSource.length()
                    > properties.maxCharacters()) {
                excludedByBudget++;
                continue;
            }
            if (!context.isEmpty()) {
                context.append(SOURCE_SEPARATOR);
            }
            context.append(formattedSource);
            sources.add(source);
        }

        return result(
                searchResponse,
                RagContextAssemblyStatus.SUCCESS,
                null,
                List.copyOf(sources),
                excludedByBudget,
                context.toString()
        );
    }

    private RagContextSource toSource(
            String citationId,
            ProjectSemanticSearchMatch match
    ) {
        return new RagContextSource(
                citationId,
                match.rank(),
                match.chunkId(),
                match.projectId(),
                match.filePath(),
                match.fileName(),
                match.extension(),
                match.chunkIndex(),
                match.startLine(),
                match.endLine(),
                match.content(),
                match.similarity(),
                match.embeddingModel()
        );
    }

    private String format(RagContextSource source) {
        return """
                [Source %s]
                Project: %s
                File: %s
                Lines: %d-%d
                --- BEGIN CONTENT ---
                %s
                --- END CONTENT ---"""
                .formatted(
                        source.citationId(),
                        source.projectId(),
                        source.filePath(),
                        source.startLine(),
                        source.endLine(),
                        source.content()
                );
    }

    private RagContextAssemblyResult result(
            ProjectSemanticSearchResponse searchResponse,
            RagContextAssemblyStatus status,
            String reason,
            List<RagContextSource> sources,
            int excludedByBudgetCount,
            String context
    ) {
        return new RagContextAssemblyResult(
                searchResponse.projectId(),
                searchResponse.query(),
                properties.maxCharacters(),
                sources.size(),
                sources.size(),
                excludedByBudgetCount,
                context.length(),
                searchResponse.status(),
                status,
                reason,
                sources,
                context
        );
    }
}
