package com.localai.workspace.workflow;

public record WorkflowEvidence(
        String id, String sourceType, String summary, String sourcePath,
        Integer startLine, Integer endLine, String status
) { }
