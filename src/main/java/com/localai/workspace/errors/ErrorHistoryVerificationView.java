package com.localai.workspace.errors;

import java.time.Instant;

public record ErrorHistoryVerificationView(Long id, Long errorHistoryId,
        ErrorStatus fromStatus, ErrorStatus toStatus, String rootCause, String solution,
        String verificationNote, String actor, Instant changedAt, long previousVersion) {
    static ErrorHistoryVerificationView from(ErrorHistoryVerification value) {
        return new ErrorHistoryVerificationView(value.id,value.errorHistoryId,value.fromStatus,value.toStatus,
                value.rootCause,value.solution,value.verificationNote,value.actor,value.changedAt,value.previousVersion);
    }
}
