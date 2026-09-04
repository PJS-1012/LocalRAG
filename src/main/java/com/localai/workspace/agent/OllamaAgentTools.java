package com.localai.workspace.agent;

import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.List;

final class OllamaAgentTools implements AgentToolTracker {

    private final OllamaReadOnlyService ollamaService;
    private final List<AgentToolInvocation> invocations = new ArrayList<>();
    private boolean failed;

    OllamaAgentTools(OllamaReadOnlyService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @Tool(
            name = "getOllamaStatus",
            description = "Read whether the configured local Ollama server is currently reachable and list its "
                    + "available model names. Use for current Ollama status or model-availability questions."
    )
    OllamaStatusResult getOllamaStatus() {
        long startedAt = System.nanoTime();
        OllamaStatusResult result = ollamaService.getStatus();
        invocations.add(new AgentToolInvocation(
                "getOllamaStatus", (System.nanoTime() - startedAt) / 1_000_000
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
