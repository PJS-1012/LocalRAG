package com.localai.workspace.automation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="detected_error_event", uniqueConstraints=@UniqueConstraint(columnNames={"project_id","fingerprint"}))
class DetectedErrorEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false,length=512) String projectId;
    @Column(nullable=false,length=64) String fingerprint;
    @Column(nullable=false) Instant detectedAt;
    @Column(length=100) String logTimestamp;
    @Column(length=1024) String sourceFile;
    Long lineNumber;
    @Column(length=30) String level;
    @Column(nullable=false,columnDefinition="text") String errorMessage;
    @Column(nullable=false,length=30) String similarLookupStatus;
    @Column(nullable=false) int similarCount;
    Long topSimilarHistoryId;
    Double topSimilarity;
    protected DetectedErrorEvent() { }
}
