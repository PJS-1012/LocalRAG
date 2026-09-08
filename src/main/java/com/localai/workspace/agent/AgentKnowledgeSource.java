package com.localai.workspace.agent;

/** Citation metadata returned to API clients; source content remains only inside the bounded Tool result. */
public record AgentKnowledgeSource(
        String id,
        String filePath,
        int startLine,
        int endLine
) {
}
