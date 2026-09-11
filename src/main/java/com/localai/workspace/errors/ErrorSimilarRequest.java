package com.localai.workspace.errors;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ErrorSimilarRequest(
        @NotBlank @Size(max = 512) String projectId,
        @Size(max = 255) String errorType,
        @NotBlank @Size(max = 4000) String errorMessage,
        @Size(max = 4000) String symptom,
        Integer topK
) { }
