package com.localai.workspace.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localai.workspace.agent.LogSecretRedactor;
import com.localai.workspace.errors.ErrorProjectScope;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class AutomationHistoryService {
    private final AutomationRunRepository repository;
    private final ErrorProjectScope scope;
    private final ObjectMapper json;
    private final LogSecretRedactor redactor;
    public AutomationHistoryService(AutomationRunRepository repository,ErrorProjectScope scope,
            ObjectMapper json,LogSecretRedactor redactor) {
        this.repository=repository;this.scope=scope;this.json=json;this.redactor=redactor;
    }

    @Transactional
    public AutomationRunView save(String project,AutomationTriggerType trigger,Instant started,
            AutomationRunStatus status,boolean changed,String summary,String error,
            AutomationRunDetails details,long duration) {
        var run=new AutomationRun(); run.projectId=project;run.triggerType=trigger;run.startedAt=started;
        run.finishedAt=Instant.now();run.status=status;run.changeDetected=changed;
        run.summary=redact(summary);run.errorMessage=redact(error);run.resultDetails=json.valueToTree(details);
        run.durationMillis=duration;
        return AutomationRunView.from(repository.saveAndFlush(run));
    }

    @Transactional(readOnly=true)
    public AutomationPage<AutomationRunView> list(String projectId,int page,int size) {
        String project=scope.require(projectId); validate(page,size);
        var result=repository.findByProjectIdOrderByStartedAtDescIdDesc(project,PageRequest.of(page,size));
        return new AutomationPage<>(result.getContent().stream().map(AutomationRunView::from).toList(),
                result.getTotalElements(),result.getTotalPages(),page,size);
    }
    private void validate(int page,int size){if(page<0||size<1||size>100)
        throw new ResponseStatusException(BAD_REQUEST,"Invalid page/size");}
    private String redact(String value){return value==null?null:redactor.redact(value);}
}
