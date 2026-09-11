package com.localai.workspace.errors;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="error_history_verification")
public class ErrorHistoryVerification {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
    @Column(nullable=false) Long errorHistoryId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) ErrorStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) ErrorStatus toStatus;
    @Column(columnDefinition="text") String rootCause;
    @Column(columnDefinition="text") String solution;
    @Column(nullable=false,columnDefinition="text") String verificationNote;
    @Column(nullable=false,length=64) String actor;
    @Column(nullable=false) Instant changedAt;
    @Column(nullable=false) long previousVersion;
    protected ErrorHistoryVerification() { }
}
