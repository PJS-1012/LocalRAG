package com.localai.workspace.errors;

import java.time.Instant;
import java.util.List;

public record ErrorSimilarMatch(
        long errorHistoryId, ErrorStatus status, String errorType, String errorMessage,
        String rootCause, String solution, Instant occurredAt, List<String> relatedFiles,
        List<String> relatedCommits, double similarity
) { }
