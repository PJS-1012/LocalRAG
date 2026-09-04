package com.localai.workspace.rag;

import com.localai.workspace.search.ProjectSemanticSearchStatus;

import java.util.List;

public record RagContextAssemblyResult(
        String projectId,
        String query,
        int contextBudgetCharacters,
        int sourceCount,
        int includedChunkCount,
        int excludedByBudgetCount,
        int totalContextCharacters,
        ProjectSemanticSearchStatus searchStatus,
        RagContextAssemblyStatus status,
        String reason,
        List<RagContextSource> sources,
        String context
) {
}
