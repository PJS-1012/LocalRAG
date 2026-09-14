package com.localai.workspace.automation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

interface ProjectAutomationConfigRepository extends JpaRepository<ProjectAutomationConfig,String> {
    List<ProjectAutomationConfig> findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(Instant now);
}
