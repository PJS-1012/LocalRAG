package com.localai.workspace.automation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationCandidateRepository extends JpaRepository<NotificationCandidate,Long> {
    boolean existsByProjectIdAndNotificationTypeAndFingerprint(
            String projectId,NotificationType type,String fingerprint);
    Page<NotificationCandidate> findByProjectIdOrderByCreatedAtDescIdDesc(String projectId, Pageable pageable);
}
