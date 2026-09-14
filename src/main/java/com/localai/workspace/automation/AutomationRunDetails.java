package com.localai.workspace.automation;

public record AutomationRunDetails(
        ChangeStatus projectChangeStatus,
        int newErrorCount,
        int duplicateErrorCount,
        int environmentFailureCount,
        boolean progressGenerated,
        boolean activityGenerated,
        boolean llmInvoked,
        long changeDetectionDurationMillis,
        long errorWatchDurationMillis,
        long environmentWatchDurationMillis,
        long workflowDurationMillis,
        long databaseDurationMillis
) { }
