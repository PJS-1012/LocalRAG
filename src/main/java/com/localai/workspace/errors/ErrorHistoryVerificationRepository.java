package com.localai.workspace.errors;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ErrorHistoryVerificationRepository extends JpaRepository<ErrorHistoryVerification,Long> {
    List<ErrorHistoryVerification> findByErrorHistoryIdOrderByChangedAtAscIdAsc(Long errorHistoryId);
    long countByErrorHistoryId(Long errorHistoryId);
}
