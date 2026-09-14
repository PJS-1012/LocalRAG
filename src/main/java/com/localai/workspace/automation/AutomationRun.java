package com.localai.workspace.automation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name="automation_run")
class AutomationRun {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,length=512) String projectId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) AutomationTriggerType triggerType;
    @Column(nullable=false) Instant startedAt;
    @Column(nullable=false) Instant finishedAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) AutomationRunStatus status;
    @Column(nullable=false) boolean changeDetected;
    @Column(columnDefinition="text") String summary;
    @Column(columnDefinition="text") String errorMessage;
    @JdbcTypeCode(SqlTypes.JSON) @Immutable
    @Column(nullable=false,columnDefinition="jsonb") JsonNode resultDetails;
    @Column(nullable=false) long durationMillis;
    protected AutomationRun() { }
}
