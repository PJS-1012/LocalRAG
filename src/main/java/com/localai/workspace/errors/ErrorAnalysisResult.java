package com.localai.workspace.errors;

import com.localai.workspace.agent.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** analysis is unverified model prose; confirmedEvidence contains actual Tool snapshots only. */
public record ErrorAnalysisResult(UUID analysisId, String projectId, Instant analyzedAt, Instant expiresAt,
        Instant occurredAt, String errorType, String errorMessage, String symptom,
        String analysis, String rootCause, String solution, ErrorStatus status, String evidenceStatus,
        List<ToolEvidence> confirmedEvidence, List<String> unknown,
        List<String> relatedFiles, List<String> relatedCommits, AgentChatResponse agent,
        long evidenceCollectionDurationMillis, long toolDurationMillis, long ragDurationMillis,
        long llmDurationMillis, long totalDurationMillis) { }
