package com.localai.workspace.errors;

import java.time.Instant;
import java.util.List;

public record ErrorSimilarResult(
        long errorHistoryId, ErrorStatus status, String trustLabel, double similarity,
        String errorType, String errorMessage, String rootCause, String solution, Instant occurredAt,
        List<String> relatedFiles, List<String> relatedCommits
) { }
