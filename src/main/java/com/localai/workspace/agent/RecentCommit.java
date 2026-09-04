package com.localai.workspace.agent;

public record RecentCommit(
        String hash,
        String message,
        String author,
        String timestamp
) {
}
