package com.localai.workspace.workflow;

import java.time.Instant;
import java.util.List;

public record ProjectProgressResponse(
        String status,
        String projectId,
        String summary,
        List<String> completed,
        List<String> inProgress,
        List<String> planned,
        List<String> blocked,
        List<String> documentationMismatch,
        List<String> unknown,
        long unresolvedErrors,
        List<WorkflowEvidence> evidence,
        List<String> toolsUsed,
        Instant generatedAt,
        long evidenceCollectionDurationMillis,
        long ragDurationMillis,
        long llmDurationMillis,
        long totalDurationMillis
) { }
