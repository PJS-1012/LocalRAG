package com.localai.workspace.automation;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AutomationSchedulerTest {
    @Test void oneProjectFailureDoesNotBlockTheNextDueProject() {
        ProjectAutomationConfigRepository configs=mock(ProjectAutomationConfigRepository.class);
        AutomationExecutionService executions=mock(AutomationExecutionService.class);
        ProjectAutomationConfig first=new ProjectAutomationConfig();first.projectId="A";
        ProjectAutomationConfig second=new ProjectAutomationConfig();second.projectId="B";
        when(configs.findByEnabledTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(any(Instant.class)))
                .thenReturn(List.of(first,second));
        when(executions.runScheduled("A")).thenThrow(new IllegalStateException("failed"));
        var scheduler=new AutomationScheduler(configs,executions);

        scheduler.runDueProjects();

        verify(executions).runScheduled("A");
        verify(executions).runScheduled("B");
    }
}
