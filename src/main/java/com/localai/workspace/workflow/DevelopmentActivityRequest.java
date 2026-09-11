package com.localai.workspace.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record DevelopmentActivityRequest(
        @NotBlank @Size(max = 512) String projectId,
        Instant since,
        Integer commitLimit
) { }
