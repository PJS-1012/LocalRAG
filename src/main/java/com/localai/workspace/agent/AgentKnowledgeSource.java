package com.localai.workspace.agent;

/** Citation metadata returned to API clients; source content remains only inside the bounded Tool result. */
public record AgentKnowledgeSource(
        String id,
        String filePath,
        int startLine,
        int endLine,
        String sourceOrigin
) {
    public AgentKnowledgeSource(String id,String filePath,int startLine,int endLine) {
        this(id,filePath,startLine,endLine,
                com.localai.workspace.overview.ProjectEvidencePolicy.origin(filePath).name());
    }
}
