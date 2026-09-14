package com.localai.workspace.automation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record AutomationRunView(
        Long id, String projectId, AutomationTriggerType triggerType, Instant startedAt, Instant finishedAt,
        AutomationRunStatus status, boolean changeDetected, String summary, String errorMessage,
        JsonNode resultDetails, long durationMillis
) {
    static AutomationRunView from(AutomationRun run) {
        return new AutomationRunView(run.id,run.projectId,run.triggerType,run.startedAt,run.finishedAt,
                run.status,run.changeDetected,run.summary,run.errorMessage,run.resultDetails.deepCopy(),run.durationMillis);
    }
}
