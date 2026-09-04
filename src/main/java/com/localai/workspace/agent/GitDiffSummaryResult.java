package com.localai.workspace.agent;

import java.util.List;

public record GitDiffSummaryResult(
        String projectId,
        GitToolStatus status,
        int changedFileCount,
        int totalAdditions,
        int totalDeletions,
        List<GitDiffFileSummary> files,
        List<String> untrackedFiles,
        String reason
) {
}
