package com.localai.workspace.overview;

import com.localai.workspace.agent.*;
import com.localai.workspace.automation.*;
import com.localai.workspace.discovery.*;
import com.localai.workspace.errors.ErrorHistoryQueryService;
import com.localai.workspace.index.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ProjectOverviewService {
    private final ProjectDiscoveryService projects;
    private final ProjectIndexService indexes;
    private final GitReadOnlyService git;
    private final DockerReadOnlyService docker;
    private final OllamaReadOnlyService ollama;
    private final DatabaseReadOnlyService database;
    private final ErrorHistoryQueryService errors;
    private final AutomationHistoryService automation;
    private final NotificationCandidateService notifications;

    public ProjectOverviewService(ProjectDiscoveryService projects,ProjectIndexService indexes,
            GitReadOnlyService git,DockerReadOnlyService docker,OllamaReadOnlyService ollama,
            DatabaseReadOnlyService database,ErrorHistoryQueryService errors,
            AutomationHistoryService automation,NotificationCandidateService notifications) {
        this.projects=projects;this.indexes=indexes;this.git=git;this.docker=docker;this.ollama=ollama;
        this.database=database;this.errors=errors;this.automation=automation;this.notifications=notifications;
    }

    public ProjectOverview get(String projectId) {
        long started=System.nanoTime();
        DetectedProject project=projects.discoverProjects().stream()
                .filter(value->value.projectId().equals(projectId)).findFirst()
                .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Project not found"));
        List<String> warnings=new ArrayList<>();
        ProjectIndexStats index=safe("Project index",warnings,()->indexes.stats(projectId));
        GitStatusResult gitStatus=safe("Git status",warnings,()->git.getStatus(projectId));
        GitRecentCommitsResult commits=safe("Recent commits",warnings,()->git.getRecentCommits(projectId,5));
        DockerStatusResult dockerStatus=safe("Docker status",warnings,docker::getStatus);
        ProjectContainerStatusResult containers=safe("Project containers",warnings,
                ()->docker.getProjectContainers(projectId));
        OllamaStatusResult ollamaStatus=safe("Ollama status",warnings,ollama::getStatus);
        DatabaseStatusResult databaseStatus=safe("Database status",warnings,database::getStatus);
        var errorState=safe("Error History",warnings,()->errors.automationState(projectId));
        AutomationPage<AutomationRunView> runs=safe("Automation History",warnings,
                ()->automation.list(projectId,0,1));
        AutomationPage<NotificationCandidateView> candidates=safe("Notifications",warnings,
                ()->notifications.list(projectId,0,100));
        AutomationRunView latest=runs==null||runs.content().isEmpty()?null:runs.content().get(0);
        long totalNotifications=candidates==null?0:candidates.totalElements();
        long unacknowledged=candidates==null?0:candidates.content().stream()
                .filter(value->value.acknowledgedAt()==null).count();
        return new ProjectOverview(project,index,gitStatus,commits,dockerStatus,containers,ollamaStatus,
                databaseStatus,errorState,latest,totalNotifications,unacknowledged,List.copyOf(warnings),
                Instant.now(),elapsed(started));
    }

    /** Cheap subset for conversational evidence: no environment inspection or LLM invocation. */
    public Map<String,Object> facts(String projectId) {
        Map<String,Object> facts=new LinkedHashMap<>();List<String> warnings=new ArrayList<>();
        facts.put("index",safe("Project index",warnings,()->indexes.stats(projectId)));
        facts.put("errorHistory",safe("Error History",warnings,()->errors.automationState(projectId)));
        var runs=safe("Automation History",warnings,()->automation.list(projectId,0,1));
        if(runs!=null&&!runs.content().isEmpty()) {
            var last=runs.content().get(0);
            facts.put("automation",Map.of("status",last.status(),"finishedAt",String.valueOf(last.finishedAt()),
                    "changeDetected",last.changeDetected()));
        } else facts.put("automation",runs==null?"UNAVAILABLE":"NO_RUNS");
        facts.put("warnings",warnings);
        return facts;
    }
    private <T> T safe(String label,List<String> warnings,Supplier<T> supplier) {
        try { return supplier.get(); }
        catch(RuntimeException exception) { warnings.add(label+" unavailable");return null; }
    }
    private long elapsed(long started){return (System.nanoTime()-started)/1_000_000;}
}
