package com.localai.workspace.automation;

import java.time.Instant;

public record NotificationCandidateView(
        Long id,String projectId,Long automationRunId,NotificationType notificationType,
        String title,String summary,Instant createdAt,Instant acknowledgedAt
) {
    static NotificationCandidateView from(NotificationCandidate value) {
        return new NotificationCandidateView(value.id,value.projectId,value.automationRunId,
                value.notificationType,value.title,value.summary,value.createdAt,value.acknowledgedAt);
    }
}
