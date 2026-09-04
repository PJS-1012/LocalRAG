package com.localai.workspace.agent;

import java.util.List;

public record GitRecentCommitsResult(
        String projectId,
        GitToolStatus status,
        int requestedLimit,
        int appliedLimit,
        List<RecentCommit> commits,
        String reason
) {
}
