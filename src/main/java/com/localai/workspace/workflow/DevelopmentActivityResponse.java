package com.localai.workspace.workflow;

import com.localai.workspace.agent.GitStatusResult;
import com.localai.workspace.agent.RecentCommit;

import java.time.Instant;
import java.util.List;

public record DevelopmentActivityResponse(
        String status,
        String projectId,
        Instant since,
        int commitLimit,
        List<RecentCommit> commits,
        List<String> changedAreas,
        String summary,
        GitStatusResult currentWorkingTree,
        List<WorkflowEvidence> relatedDecisionLogs,
        Instant generatedAt,
        long gitEvidenceDurationMillis,
        long ragDurationMillis,
        long llmDurationMillis,
        long totalDurationMillis
) { }
