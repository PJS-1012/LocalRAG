package com.localai.workspace.errors;

import java.util.List;

public record ErrorHistoryDetail(ErrorHistoryView history,
        List<ErrorHistoryVerificationView> verifications, long queryDurationMillis) { }
