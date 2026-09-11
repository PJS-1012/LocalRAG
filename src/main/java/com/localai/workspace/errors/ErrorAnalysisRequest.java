package com.localai.workspace.errors;
import jakarta.validation.constraints.*;
public record ErrorAnalysisRequest(@NotBlank @Size(max=512) String projectId,
        @NotBlank @Size(max=2000) String query) { }
