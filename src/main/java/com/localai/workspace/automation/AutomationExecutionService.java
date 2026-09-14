package com.localai.workspace.automation;

import com.localai.workspace.agent.LogSecretRedactor;
import com.localai.workspace.errors.ErrorProjectScope;
import com.localai.workspace.workflow.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AutomationExecutionService {
    private final ProjectAutomationConfigRepository configs;
    private final ErrorProjectScope scope;
    private final ProjectStateFingerprintService states;
    private final ErrorWatchService errors;
    private final EnvironmentWatchService environments;
    private final ProjectProgressService progressService;
    private final DevelopmentActivityService activityService;
    private final AutomationHistoryService history;
    private final NotificationCandidateService notifications;
    private final LogSecretRedactor redactor;
    private final Set<String> running=ConcurrentHashMap.newKeySet();

    public AutomationExecutionService(ProjectAutomationConfigRepository configs,ErrorProjectScope scope,
            ProjectStateFingerprintService states,ErrorWatchService errors,
            EnvironmentWatchService environments,ProjectProgressService progressService,
            DevelopmentActivityService activityService,AutomationHistoryService history,
            NotificationCandidateService notifications,LogSecretRedactor redactor) {
        this.configs=configs;this.scope=scope;this.states=states;this.errors=errors;this.environments=environments;
        this.progressService=progressService;this.activityService=activityService;this.history=history;
        this.notifications=notifications;this.redactor=redactor;
    }

    public AutomationExecutionResult runManual(String projectId) {
        String project=scope.require(projectId);
        ProjectAutomationConfig config=configs.findById(project)
                .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Automation configuration not found"));
        return run(config.projectId,AutomationTriggerType.MANUAL);
    }

    public AutomationExecutionResult runScheduled(String projectId) {
        return run(projectId,AutomationTriggerType.SCHEDULED);
    }

    private AutomationExecutionResult run(String projectId,AutomationTriggerType trigger) {
        long totalStarted=System.nanoTime();
        if(!running.add(projectId)) return new AutomationExecutionResult(AutomationRunStatus.ALREADY_RUNNING,
                projectId,trigger,false,false,"Automation is already running for this Project",
                null,null,null,null,null,null,List.of(),elapsed(totalStarted));
        try {
            ProjectAutomationConfig config=configs.findById(projectId)
                    .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Automation configuration not found"));
            if(trigger==AutomationTriggerType.SCHEDULED && !config.enabled)
                return new AutomationExecutionResult(AutomationRunStatus.DISABLED,projectId,trigger,false,false,
                        "Automation is disabled",null,null,null,null,null,null,List.of(),elapsed(totalStarted));
            return execute(config,trigger,totalStarted);
        } finally { running.remove(projectId); }
    }

    private AutomationExecutionResult execute(ProjectAutomationConfig config,AutomationTriggerType trigger,
            long totalStarted) {
        Instant startedAt=Instant.now();
        List<String> failures=new ArrayList<>();
        ProjectStateSnapshot projectState;
        try { projectState=states.capture(config.projectId); }
        catch(RuntimeException exception) {
            return failed(config,trigger,startedAt,totalStarted,"Project change detection failed");
        }
        boolean projectChanged=!Objects.equals(config.lastProjectFingerprint,projectState.fingerprint());

        ErrorWatchResult errorWatch=null;
        if(config.errorWatchEnabled) {
            try { errorWatch=errors.watch(config.projectId); }
            catch(RuntimeException exception) { failures.add("Error Watch failed"); }
        }
        int newErrors=errorWatch==null?0:errorWatch.newErrorCount();
        String logFingerprint=errorWatch==null?config.lastLogFingerprint:errorWatch.logFingerprint();

        EnvironmentWatchResult environment=null;
        if(config.environmentWatchEnabled) {
            try { environment=environments.watch(config.projectId); }
            catch(RuntimeException exception) { failures.add("Environment Watch failed"); }
        }
        String environmentFingerprint=environment==null?config.lastEnvironmentFingerprint:environment.fingerprint();
        boolean environmentChanged=environment!=null
                && !Objects.equals(config.lastEnvironmentFingerprint,environment.fingerprint());
        boolean changeDetected=projectChanged||newErrors>0||environmentChanged;

        ProjectProgressResponse progress=null;
        DevelopmentActivityResponse activity=null;
        boolean llmInvoked=false;
        long workflowStarted=System.nanoTime();
        if(projectChanged && config.progressSummaryEnabled) {
            llmInvoked=true;
            try { progress=progressService.analyze(new ProjectProgressRequest(config.projectId)); }
            catch(RuntimeException exception) { failures.add("Progress Analysis failed"); }
        }
        if(projectChanged && config.activitySummaryEnabled) {
            llmInvoked=true;
            try { activity=activityService.summarize(new DevelopmentActivityRequest(config.projectId,
                    config.lastRunAt,5)); }
            catch(RuntimeException exception) { failures.add("Recent Development Summary failed"); }
        }
        long workflowMillis=elapsed(workflowStarted);
        AutomationRunStatus status=!changeDetected&&failures.isEmpty()?AutomationRunStatus.NO_CHANGE
                :failures.isEmpty()?AutomationRunStatus.SUCCESS:AutomationRunStatus.PARTIAL_SUCCESS;
        String summary=summary(projectChanged,newErrors,environment,progress,activity);
        String errorMessage=failures.isEmpty()?null:String.join("; ",failures);

        config.lastProjectFingerprint=projectState.fingerprint();
        config.lastLogFingerprint=logFingerprint;
        config.lastEnvironmentFingerprint=environmentFingerprint;
        config.lastRunAt=Instant.now();
        config.nextRunAt=config.enabled?config.lastRunAt.plusSeconds(config.intervalSeconds):null;
        config.updatedAt=config.lastRunAt;
        long databaseStarted=System.nanoTime();
        configs.saveAndFlush(config);
        long configDatabaseMillis=elapsed(databaseStarted);
        long errorDb=errorWatch==null?0:errorWatch.databaseDurationMillis();
        var details=new AutomationRunDetails(projectChanged?ChangeStatus.CHANGED:ChangeStatus.NO_CHANGE,
                newErrors,errorWatch==null?0:errorWatch.duplicateCount(),
                environment==null?0:environment.failureCount(),progress!=null,activity!=null,llmInvoked,
                projectState.durationMillis(),errorWatch==null?0:errorWatch.totalDurationMillis(),
                environment==null?0:environment.durationMillis(),workflowMillis,errorDb+configDatabaseMillis);
        AutomationRunView run=history.save(config.projectId,trigger,startedAt,status,changeDetected,
                summary,errorMessage,details,elapsed(totalStarted));
        List<NotificationCandidateView> created=createNotifications(config,run,projectChanged,errorWatch,environment,
                progress);
        return new AutomationExecutionResult(status,config.projectId,trigger,changeDetected,llmInvoked,errorMessage,
                projectState,errorWatch,environment,progress,activity,run,created,elapsed(totalStarted));
    }

    private AutomationExecutionResult failed(ProjectAutomationConfig config,AutomationTriggerType trigger,
            Instant startedAt,long totalStarted,String reason) {
        long duration=elapsed(totalStarted);
        var details=new AutomationRunDetails(ChangeStatus.FAILED,0,0,0,false,false,false,duration,0,0,0,0);
        AutomationRunView run=history.save(config.projectId,trigger,startedAt,AutomationRunStatus.FAILED,false,
                "Automation failed before change detection completed",reason,details,duration);
        return new AutomationExecutionResult(AutomationRunStatus.FAILED,config.projectId,trigger,false,false,reason,
                null,null,null,null,null,run,List.of(),elapsed(totalStarted));
    }

    private List<NotificationCandidateView> createNotifications(ProjectAutomationConfig config,AutomationRunView run,
            boolean projectChanged,ErrorWatchResult errors,EnvironmentWatchResult environment,
            ProjectProgressResponse progress) {
        List<NotificationCandidateView> result=new ArrayList<>();
        if(projectChanged) add(result,config.projectId,run.id(),NotificationType.PROJECT_CHANGED,
                config.lastProjectFingerprint,"Project state changed","Git, Error History, or Project Index state changed");
        if(progress!=null) add(result,config.projectId,run.id(),NotificationType.PROGRESS_UPDATED,
                config.lastProjectFingerprint,"Project progress updated","A new evidence-based progress snapshot is available");
        if(errors!=null) for(DetectedErrorCandidate error:errors.newErrors()) add(result,config.projectId,run.id(),
                NotificationType.ERROR_DETECTED,error.fingerprint(),"New error detected",
                error.sourceFile()+": "+error.errorMessage());
        if(environment!=null&&environment.failureCount()>0) add(result,config.projectId,run.id(),
                NotificationType.ENVIRONMENT_FAILURE,environment.fingerprint(),"Environment check requires attention",
                environment.failureCount()+" environment observation(s) are unavailable or failed");
        return List.copyOf(result);
    }

    private void add(List<NotificationCandidateView> target,String project,Long runId,NotificationType type,
            String fingerprint,String title,String summary) {
        notifications.create(project,runId,type,fingerprint,title,summary).ifPresent(target::add);
    }

    private String summary(boolean projectChanged,int newErrors,EnvironmentWatchResult environment,
            ProjectProgressResponse progress,DevelopmentActivityResponse activity) {
        return redactor.redact("projectChanged="+projectChanged+", newErrors="+newErrors
                +", environmentFailures="+(environment==null?0:environment.failureCount())
                +", progressGenerated="+(progress!=null)+", activityGenerated="+(activity!=null));
    }
    private long elapsed(long started){return (System.nanoTime()-started)/1_000_000;}
}
