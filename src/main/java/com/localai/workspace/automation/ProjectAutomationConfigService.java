package com.localai.workspace.automation;

import com.localai.workspace.errors.ErrorProjectScope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ProjectAutomationConfigService {
    private final ProjectAutomationConfigRepository repository;
    private final ErrorProjectScope scope;
    public ProjectAutomationConfigService(ProjectAutomationConfigRepository repository,ErrorProjectScope scope) {
        this.repository=repository; this.scope=scope;
    }

    @Transactional(readOnly=true)
    public ProjectAutomationConfigView get(String projectId) {
        String project=scope.require(projectId);
        return repository.findById(project).map(ProjectAutomationConfigView::from)
                .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Automation configuration not found"));
    }

    @Transactional
    public ProjectAutomationConfigView put(ProjectAutomationConfigRequest request) {
        if(request.intervalSeconds()<60||request.intervalSeconds()>604800)
            throw new ResponseStatusException(BAD_REQUEST,"Automation interval must be between 60 and 604800 seconds");
        String project=scope.require(request.projectId());
        Instant now=Instant.now();
        ProjectAutomationConfig config=repository.findById(project).orElseGet(()->{
            var created=new ProjectAutomationConfig(); created.projectId=project; created.createdAt=now; return created;
        });
        config.enabled=request.enabled(); config.progressSummaryEnabled=request.progressSummaryEnabled();
        config.activitySummaryEnabled=request.activitySummaryEnabled(); config.errorWatchEnabled=request.errorWatchEnabled();
        config.environmentWatchEnabled=request.environmentWatchEnabled(); config.intervalSeconds=request.intervalSeconds();
        config.updatedAt=now;
        config.nextRunAt=config.enabled ? now.plusSeconds(config.intervalSeconds) : null;
        return ProjectAutomationConfigView.from(repository.saveAndFlush(config));
    }
}
