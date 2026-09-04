package com.localai.workspace.agent;

public record GitDiffFileSummary(
        String filePath,
        Integer additions,
        Integer deletions,
        boolean binary
) {
}
