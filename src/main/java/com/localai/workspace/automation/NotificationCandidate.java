package com.localai.workspace.automation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="notification_candidate", uniqueConstraints=@UniqueConstraint(
        columnNames={"project_id","notification_type","fingerprint"}))
class NotificationCandidate {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,length=512) String projectId;
    Long automationRunId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) NotificationType notificationType;
    @Column(nullable=false,length=64) String fingerprint;
    @Column(nullable=false,length=255) String title;
    @Column(nullable=false,columnDefinition="text") String summary;
    @Column(nullable=false) Instant createdAt;
    Instant acknowledgedAt;
    protected NotificationCandidate() { }
}
