package com.localai.workspace.automation;

import com.localai.workspace.workflow.DevelopmentActivityResponse;
import com.localai.workspace.workflow.ProjectProgressResponse;
import java.util.List;

public record AutomationExecutionResult(
        AutomationRunStatus status,
        String projectId,
        AutomationTriggerType triggerType,
        boolean changeDetected,
        boolean llmInvoked,
        String reason,
        ProjectStateSnapshot projectState,
        ErrorWatchResult errorWatch,
        EnvironmentWatchResult environmentWatch,
        ProjectProgressResponse progress,
        DevelopmentActivityResponse activity,
        AutomationRunView run,
        List<NotificationCandidateView> notifications,
        long totalDurationMillis
) { }
