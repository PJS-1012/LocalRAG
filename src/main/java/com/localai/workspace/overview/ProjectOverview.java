package com.localai.workspace.overview;

import com.localai.workspace.agent.*;
import com.localai.workspace.automation.*;
import com.localai.workspace.discovery.DetectedProject;
import com.localai.workspace.errors.ErrorHistoryQueryService;
import com.localai.workspace.index.ProjectIndexStats;

import java.time.Instant;
import java.util.List;

public record ProjectOverview(
        DetectedProject project,
        ProjectIndexStats index,
        GitStatusResult git,
        GitRecentCommitsResult recentCommits,
        DockerStatusResult docker,
        ProjectContainerStatusResult projectContainers,
        OllamaStatusResult ollama,
        DatabaseStatusResult database,
        ErrorHistoryQueryService.AutomationState errors,
        AutomationRunView latestAutomation,
        long notificationCandidateCount,
        long unacknowledgedNotificationCount,
        List<String> warnings,
        Instant collectedAt,
        long durationMillis
) { }
