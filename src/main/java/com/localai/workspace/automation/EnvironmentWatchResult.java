package com.localai.workspace.automation;

import java.util.List;

public record EnvironmentWatchResult(
        String projectId,String fingerprint,int failureCount,long durationMillis,
        List<EnvironmentObservation> observations
) { }
