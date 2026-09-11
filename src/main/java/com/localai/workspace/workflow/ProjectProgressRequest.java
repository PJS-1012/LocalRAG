package com.localai.workspace.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectProgressRequest(@NotBlank @Size(max = 512) String projectId) { }
