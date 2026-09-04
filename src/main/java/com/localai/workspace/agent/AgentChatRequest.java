package com.localai.workspace.agent;

import jakarta.validation.constraints.NotBlank;

public record AgentChatRequest(
        @NotBlank String projectId,
        @NotBlank String query
) {
}
