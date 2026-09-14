package com.localai.workspace.automation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="project_automation_config")
class ProjectAutomationConfig {
    @Id @Column(length=512) String projectId;
    @Column(nullable=false) boolean enabled;
    @Column(nullable=false) boolean progressSummaryEnabled;
    @Column(nullable=false) boolean activitySummaryEnabled;
    @Column(nullable=false) boolean errorWatchEnabled;
    @Column(nullable=false) boolean environmentWatchEnabled;
    @Column(nullable=false) int intervalSeconds;
    Instant lastRunAt;
    Instant nextRunAt;
    @Column(length=64) String lastProjectFingerprint;
    @Column(length=64) String lastLogFingerprint;
    @Column(length=64) String lastEnvironmentFingerprint;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant updatedAt;
    @Version long version;
    protected ProjectAutomationConfig() { }
}
