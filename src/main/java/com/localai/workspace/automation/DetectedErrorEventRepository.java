package com.localai.workspace.automation;

import org.springframework.data.jpa.repository.JpaRepository;

interface DetectedErrorEventRepository extends JpaRepository<DetectedErrorEvent,Long> {
    boolean existsByProjectIdAndFingerprint(String projectId,String fingerprint);
}
