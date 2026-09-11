package com.localai.workspace.errors;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Immutable;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="error_history")
public class ErrorHistory {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false, unique=true) UUID analysisId;
    @Column(nullable=false, length=512) String projectId;
    Instant occurredAt;
    @Column(nullable=false) Instant recordedAt;
    @Column(columnDefinition="text") String errorType;
    @Column(columnDefinition="text") String errorMessage;
    @Column(nullable=false, columnDefinition="text") String symptom;
    @Column(columnDefinition="text") String rootCause;
    @Column(columnDefinition="text") String solution;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) ErrorStatus status;
    @JdbcTypeCode(SqlTypes.JSON) @Immutable
    @Column(nullable=false, columnDefinition="jsonb") List<String> relatedFiles;
    @JdbcTypeCode(SqlTypes.JSON) @Immutable
    @Column(nullable=false, columnDefinition="jsonb") List<String> relatedCommits;
    @JdbcTypeCode(SqlTypes.JSON) @Immutable
    @Column(nullable=false, columnDefinition="jsonb") JsonNode evidenceSummary;
    @Column(nullable=false, length=64) String createdBy;
    @Column(columnDefinition="text") String verificationNote;
    @Column(length=64) String verifiedBy;
    Instant statusChangedAt;
    @Version long version;
    protected ErrorHistory() { }
}
