package com.localai.workspace.automation;

import java.time.Instant;

public record ProjectAutomationConfigView(
        String projectId, boolean enabled, boolean progressSummaryEnabled,
        boolean activitySummaryEnabled, boolean errorWatchEnabled, boolean environmentWatchEnabled,
        int intervalSeconds, Instant lastRunAt, Instant nextRunAt, Instant createdAt, Instant updatedAt,
        long version
) {
    static ProjectAutomationConfigView from(ProjectAutomationConfig config) {
        return new ProjectAutomationConfigView(config.projectId,config.enabled,config.progressSummaryEnabled,
                config.activitySummaryEnabled,config.errorWatchEnabled,config.environmentWatchEnabled,
                config.intervalSeconds,config.lastRunAt,config.nextRunAt,config.createdAt,config.updatedAt,
                config.version);
    }
}
