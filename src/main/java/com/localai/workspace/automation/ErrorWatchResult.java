package com.localai.workspace.automation;

import java.util.List;

public record ErrorWatchResult(
        String projectId,String status,String logFingerprint,int scannedResultCount,
        int newErrorCount,int duplicateCount,long logDurationMillis,long similarityDurationMillis,
        long databaseDurationMillis,long totalDurationMillis,List<DetectedErrorCandidate> newErrors,
        String reason
) { }
