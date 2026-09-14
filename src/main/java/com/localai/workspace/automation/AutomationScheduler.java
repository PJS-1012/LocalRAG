package com.localai.workspace.automation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
@ConditionalOnProperty(prefix="localrag.automation",name="scheduler-enabled",havingValue="true",matchIfMissing=true)
public class AutomationScheduler {
    private final ProjectAutomationConfigRepository configs;
    private final AutomationExecutionService executions;
    public AutomationScheduler(ProjectAutomationConfigRepository configs,AutomationExecutionService executions) {
        this.configs=configs;this.executions=executions;
    }

    @Scheduled(fixedDelayString="${localrag.automation.poll-delay:PT30S}",
            initialDelayString="${localrag.automation.initial-delay:PT1M}")
    public void runDueProjects() {
        for(ProjectAutomationConfig config:configs
                .findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(Instant.now())) {
            try { executions.runScheduled(config.projectId); }
            catch(RuntimeException ignored) {
                // Project failure isolation: the next due Project must still run.
            }
        }
    }
}
