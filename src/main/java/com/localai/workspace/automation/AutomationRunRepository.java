package com.localai.workspace.automation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface AutomationRunRepository extends JpaRepository<AutomationRun,Long> {
    Page<AutomationRun> findByProjectIdOrderByStartedAtDescIdDesc(String projectId, Pageable pageable);
}
