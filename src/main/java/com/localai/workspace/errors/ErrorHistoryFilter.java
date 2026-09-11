package com.localai.workspace.errors;

import java.time.Instant;

public record ErrorHistoryFilter(String projectId, ErrorStatus status, String errorType,
        String errorMessage, Instant occurredFrom, Instant occurredTo,
        Instant recordedFrom, Instant recordedTo, String relatedFile, String relatedCommit,
        int page, int size) { }
