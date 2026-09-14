package com.localai.workspace.automation;

import java.util.List;

public record ProjectStateSnapshot(
        String projectId,
        String fingerprint,
        String gitHead,
        String workingTreeFingerprint,
        String errorHistoryFingerprint,
        String projectIndexFingerprint,
        long unresolvedErrors,
        long durationMillis,
        List<String> issues
) { }
