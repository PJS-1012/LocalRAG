package com.localai.workspace.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AutomationRunRequest(@NotBlank @Size(max=512) String projectId) { }
