package com.localai.workspace.automation;

import com.localai.workspace.agent.LogSecretRedactor;
import com.localai.workspace.errors.ErrorProjectScope;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Optional;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class NotificationCandidateService {
    private final NotificationCandidateRepository repository;
    private final ErrorProjectScope scope;
    private final LogSecretRedactor redactor;
    public NotificationCandidateService(NotificationCandidateRepository repository,ErrorProjectScope scope,
            LogSecretRedactor redactor){this.repository=repository;this.scope=scope;this.redactor=redactor;}

    @Transactional
    public Optional<NotificationCandidateView> create(String projectId,Long runId,NotificationType type,
            String fingerprint,String title,String summary) {
        if(repository.existsByProjectIdAndNotificationTypeAndFingerprint(projectId,type,fingerprint))
            return Optional.empty();
        var value=new NotificationCandidate();value.projectId=projectId;value.automationRunId=runId;
        value.notificationType=type;value.fingerprint=fingerprint;value.title=bounded(redactor.redact(title),255);
        value.summary=redactor.redact(summary);value.createdAt=Instant.now();
        return Optional.of(NotificationCandidateView.from(repository.saveAndFlush(value)));
    }

    @Transactional(readOnly=true)
    public AutomationPage<NotificationCandidateView> list(String projectId,int page,int size) {
        String project=scope.require(projectId);if(page<0||size<1||size>100)
            throw new ResponseStatusException(BAD_REQUEST,"Invalid page/size");
        var result=repository.findByProjectIdOrderByCreatedAtDescIdDesc(project,PageRequest.of(page,size));
        return new AutomationPage<>(result.getContent().stream().map(NotificationCandidateView::from).toList(),
                result.getTotalElements(),result.getTotalPages(),page,size);
    }
    private String bounded(String value,int max){return value.length()<=max?value:value.substring(0,max);}
}
