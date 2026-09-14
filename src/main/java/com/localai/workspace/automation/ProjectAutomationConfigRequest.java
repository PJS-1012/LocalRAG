package com.localai.workspace.automation;

import jakarta.validation.constraints.*;

public record ProjectAutomationConfigRequest(
        @NotBlank @Size(max=512) String projectId,
        boolean enabled,
        boolean progressSummaryEnabled,
        boolean activitySummaryEnabled,
        boolean errorWatchEnabled,
        boolean environmentWatchEnabled,
        @Min(60) @Max(604800) int intervalSeconds
) { }
