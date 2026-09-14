package com.localai.workspace.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localai.workspace.agent.LogSecretRedactor;
import com.localai.workspace.errors.ErrorProjectScope;
import com.localai.workspace.workflow.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutomationExecutionServiceTest {
    final ProjectAutomationConfigRepository configs=mock(ProjectAutomationConfigRepository.class);
    final ErrorProjectScope scope=mock(ErrorProjectScope.class);
    final ProjectStateFingerprintService states=mock(ProjectStateFingerprintService.class);
    final ErrorWatchService errors=mock(ErrorWatchService.class);
    final EnvironmentWatchService environments=mock(EnvironmentWatchService.class);
    final ProjectProgressService progress=mock(ProjectProgressService.class);
    final DevelopmentActivityService activity=mock(DevelopmentActivityService.class);
    final AutomationHistoryService history=mock(AutomationHistoryService.class);
    final NotificationCandidateService notifications=mock(NotificationCandidateService.class);
    final AutomationExecutionService service=new AutomationExecutionService(configs,scope,states,errors,environments,
            progress,activity,history,notifications,new LogSecretRedactor());

    AutomationExecutionServiceTest() { when(scope.require("P")).thenReturn("P"); }

    @Test void noChangeSkipsBothLlmWorkflowsAndPersistsCheapRun() {
        ProjectAutomationConfig config=config("P","same","logs","env",true);
        when(configs.findById("P")).thenReturn(Optional.of(config));
        when(configs.saveAndFlush(config)).thenReturn(config);
        when(states.capture("P")).thenReturn(state("P","same"));
        when(errors.watch("P")).thenReturn(error("P","logs",0,1));
        when(environments.watch("P")).thenReturn(environment("P","env",0));
        when(history.save(anyString(),any(),any(),any(),anyBoolean(),anyString(),nullable(String.class),any(),anyLong()))
                .thenReturn(run("P",AutomationRunStatus.NO_CHANGE));

        var result=service.runManual("P");

        assertThat(result.status()).isEqualTo(AutomationRunStatus.NO_CHANGE);
        assertThat(result.llmInvoked()).isFalse();
        verifyNoInteractions(progress,activity);
        verify(history).save(eq("P"),eq(AutomationTriggerType.MANUAL),any(),
                eq(AutomationRunStatus.NO_CHANGE),eq(false),anyString(),isNull(),any(),anyLong());
    }

    @Test void projectChangeRunsEnabledWorkflowsAndOneFailureDoesNotDiscardOtherResult() {
        ProjectAutomationConfig config=config("P","old","logs","env",true);
        when(configs.findById("P")).thenReturn(Optional.of(config));
        when(configs.saveAndFlush(config)).thenReturn(config);
        when(states.capture("P")).thenReturn(state("P","new"));
        when(errors.watch("P")).thenReturn(error("P","logs",0,0));
        when(environments.watch("P")).thenReturn(environment("P","env",0));
        when(progress.analyze(any())).thenThrow(new IllegalStateException("model unavailable"));
        when(activity.summarize(any())).thenReturn(activity("P"));
        when(history.save(anyString(),any(),any(),any(),anyBoolean(),anyString(),anyString(),any(),anyLong()))
                .thenReturn(run("P",AutomationRunStatus.PARTIAL_SUCCESS));

        var result=service.runManual("P");

        assertThat(result.status()).isEqualTo(AutomationRunStatus.PARTIAL_SUCCESS);
        assertThat(result.llmInvoked()).isTrue();
        assertThat(result.activity()).isNotNull();
        assertThat(result.reason()).contains("Progress Analysis failed").doesNotContain("model unavailable");
        verify(progress).analyze(any()); verify(activity).summarize(any());
    }

    @Test void sameProjectSecondRunIsRejectedWhileFirstIsStillCapturing() throws Exception {
        ProjectAutomationConfig config=config("P","old","logs","env",true);
        when(configs.findById("P")).thenReturn(Optional.of(config));
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(states.capture("P")).thenAnswer(inv->{entered.countDown();release.await(5,TimeUnit.SECONDS);return state("P","old");});
        when(errors.watch("P")).thenReturn(error("P","logs",0,0));
        when(environments.watch("P")).thenReturn(environment("P","env",0));
        when(configs.saveAndFlush(config)).thenReturn(config);
        when(history.save(anyString(),any(),any(),any(),anyBoolean(),anyString(),nullable(String.class),any(),anyLong()))
                .thenReturn(run("P",AutomationRunStatus.NO_CHANGE));
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            Future<AutomationExecutionResult> first=executor.submit(()->service.runManual("P"));
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            assertThat(service.runManual("P").status()).isEqualTo(AutomationRunStatus.ALREADY_RUNNING);
            release.countDown();
            assertThat(first.get(5,TimeUnit.SECONDS).status()).isEqualTo(AutomationRunStatus.NO_CHANGE);
        } finally {release.countDown();executor.shutdownNow();}
    }

    @Test void scheduledDisabledProjectDoesNotCollectEvidenceButManualStillCan() {
        ProjectAutomationConfig config=config("P","same","logs","env",false);
        when(configs.findById("P")).thenReturn(Optional.of(config));
        assertThat(service.runScheduled("P").status()).isEqualTo(AutomationRunStatus.DISABLED);
        verifyNoInteractions(states,errors,environments,progress,activity);
    }

    private ProjectAutomationConfig config(String project,String state,String logs,String env,boolean enabled) {
        var value=new ProjectAutomationConfig();value.projectId=project;value.enabled=enabled;
        value.progressSummaryEnabled=true;value.activitySummaryEnabled=true;value.errorWatchEnabled=true;
        value.environmentWatchEnabled=true;value.intervalSeconds=300;value.lastProjectFingerprint=state;
        value.lastLogFingerprint=logs;value.lastEnvironmentFingerprint=env;value.createdAt=Instant.now();
        value.updatedAt=Instant.now();return value;
    }
    private ProjectStateSnapshot state(String project,String value) {
        return new ProjectStateSnapshot(project,value,"head","work","history","index",0,1,List.of());
    }
    private ErrorWatchResult error(String project,String fp,int count,int duplicates) {
        return new ErrorWatchResult(project,"SUCCESS",fp,count,count,duplicates,1,0,1,2,List.of(),null);
    }
    private EnvironmentWatchResult environment(String project,String fp,int failures) {
        return new EnvironmentWatchResult(project,fp,failures,1,List.of());
    }
    private DevelopmentActivityResponse activity(String project) {
        return new DevelopmentActivityResponse("SUCCESS",project,null,5,List.of(),List.of(),"summary",null,
                List.of(),Instant.now(),1,1,1,3);
    }
    private AutomationRunView run(String project,AutomationRunStatus status) {
        return new AutomationRunView(1L,project,AutomationTriggerType.MANUAL,Instant.now(),Instant.now(),status,
                status!=AutomationRunStatus.NO_CHANGE,"summary",null,new ObjectMapper().createObjectNode(),3);
    }
}
