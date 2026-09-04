package com.localai.workspace.agent;

import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.List;

final class DatabaseAgentTools implements AgentToolTracker {

    private final DatabaseReadOnlyService databaseService;
    private final List<AgentToolInvocation> invocations = new ArrayList<>();
    private boolean failed;

    DatabaseAgentTools(DatabaseReadOnlyService databaseService) {
        this.databaseService = databaseService;
    }

    @Tool(
            name = "getDatabaseStatus",
            description = "Read whether the LocalRAG PostgreSQL database is currently reachable from the application "
                    + "and whether the pgvector extension is available. Never returns credentials or connection secrets."
    )
    DatabaseStatusResult getDatabaseStatus() {
        long startedAt = System.nanoTime();
        DatabaseStatusResult result = databaseService.getStatus();
        invocations.add(new AgentToolInvocation(
                "getDatabaseStatus", (System.nanoTime() - startedAt) / 1_000_000
        ));
        failed = result.status() != LocalEnvironmentStatus.AVAILABLE;
        return result;
    }

    @Override
    public List<AgentToolInvocation> invocations() {
        return List.copyOf(invocations);
    }

    @Override
    public boolean failed() {
        return failed;
    }
}
