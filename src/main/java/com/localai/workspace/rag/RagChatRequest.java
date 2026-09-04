package com.localai.workspace.rag;

import jakarta.validation.constraints.NotBlank;

public record RagChatRequest(
        @NotBlank String projectId,
        @NotBlank String query
) {
}
