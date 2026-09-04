package com.localai.workspace.agent;

import java.util.List;

public record OllamaStatusResult(
        LocalEnvironmentStatus status,
        boolean reachable,
        int modelCount,
        List<String> models,
        String reason
) {
}
