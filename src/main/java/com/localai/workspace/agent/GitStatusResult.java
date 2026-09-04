package com.localai.workspace.agent;

import java.util.List;

public record GitStatusResult(
        String projectId,
        GitToolStatus status,
        String branch,
        boolean clean,
        List<String> modified,
        List<String> added,
        List<String> deleted,
        List<String> untracked,
        String reason
) {
}
