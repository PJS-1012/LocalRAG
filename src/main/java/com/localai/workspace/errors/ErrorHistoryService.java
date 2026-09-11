package com.localai.workspace.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localai.workspace.agent.LogSecretRedactor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class ErrorHistoryService {
    private final ErrorHistoryRepository repository;
    private final ErrorAnalysisService analyses;
    private final ErrorProjectScope scope;
    private final ErrorHistoryVerificationRepository verifications;
    private final LogSecretRedactor redactor;
    private final ObjectMapper json;
    private final ErrorHistoryEmbeddingService embeddingIndexer;
    public record SaveResult(ErrorHistoryView history, long dbSaveDurationMillis, boolean alreadySaved,
            ErrorEmbeddingIndexResult embeddingIndex) { }
    public record HistoryPage(List<ErrorHistoryView> content, long totalElements, int totalPages,
            int page, int size, long queryDurationMillis) { }
    public record StatusChangeResult(ErrorHistoryView history, ErrorHistoryVerificationView verification,
            long stateChangeDurationMillis, long auditSaveDurationMillis) { }
    public ErrorHistoryService(ErrorHistoryRepository repository, ErrorAnalysisService analyses,
            ErrorProjectScope scope, ErrorHistoryVerificationRepository verifications,
            LogSecretRedactor redactor, ObjectMapper json, ErrorHistoryEmbeddingService embeddingIndexer) {
        this.repository=repository; this.analyses=analyses; this.scope=scope; this.verifications=verifications;
        this.redactor=redactor; this.json=json; this.embeddingIndexer=embeddingIndexer;
    }
    @Transactional
    public SaveResult saveErrorHistory(ErrorHistorySaveRequest request) {
        String project=scope.require(request.projectId());
        long started=System.nanoTime();
        var existing=repository.findByAnalysisIdAndProjectId(request.analysisId(),project);
        if (existing.isPresent()) {
            var index=embeddingIndexer.index(existing.get());
            return new SaveResult(ErrorHistoryView.from(existing.get()),elapsed(started),true,index);
        }
        var draft=analyses.draft(request.analysisId(),project);
        ErrorHistory h=new ErrorHistory();
        h.analysisId=draft.analysisId(); h.projectId=project;
        h.occurredAt=draft.occurredAt(); h.recordedAt=Instant.now();
        h.errorType=redactor.redact(draft.errorType()); h.errorMessage=redactor.redact(draft.errorMessage());
        h.symptom=redactor.redact(draft.symptom()); h.status=ErrorStatus.UNVERIFIED;
        h.rootCause=null; h.solution=null;
        h.relatedFiles=java.util.List.copyOf(draft.relatedFiles());
        h.relatedCommits=java.util.List.copyOf(draft.relatedCommits());
        // Defense in depth: sanitize the entire server snapshot again at the persistence boundary.
        h.evidenceSummary=analyses.sanitize(json.valueToTree(draft));
        h.createdBy="LOCAL_USER_REQUESTED";
        repository.saveAndFlush(h);
        var index=embeddingIndexer.index(h);
        return new SaveResult(ErrorHistoryView.from(h),elapsed(started),false,index);
    }
    @Transactional(readOnly=true)
    public HistoryPage list(String projectId,int page,int size) {
        return list(new ErrorHistoryFilter(projectId,null,null,null,null,null,null,null,null,null,page,size));
    }
    @Transactional(readOnly=true)
    public HistoryPage list(ErrorHistoryFilter filter) {
        long started=System.nanoTime();
        String project=scope.require(filter.projectId());
        if(filter.page()<0 || filter.size()<1 || filter.size()>100)
            throw new ResponseStatusException(BAD_REQUEST,"Invalid page/size");
        validateRange(filter.occurredFrom(),filter.occurredTo(),"occurredAt");
        validateRange(filter.recordedFrom(),filter.recordedTo(),"recordedAt");
        String type=normalized(filter.errorType(),255,"errorType");
        if(type!=null) type=type.toLowerCase(Locale.ROOT);
        String message=normalized(filter.errorMessage(),1000,"errorMessage");
        if(message!=null) message="%"+escapeLike(message.toLowerCase(Locale.ROOT))+"%";
        String file=normalizedFile(filter.relatedFile());
        String commit=normalized(filter.relatedCommit(),40,"relatedCommit");
        if(commit!=null && !commit.matches("[a-fA-F0-9]{7,40}"))
            throw new ResponseStatusException(BAD_REQUEST,"Invalid relatedCommit");
        var result=repository.search(project,filter.status()==null?null:filter.status().name(),type,message,
                filter.occurredFrom(),filter.occurredTo(),filter.recordedFrom(),filter.recordedTo(),
                file,commit,PageRequest.of(filter.page(),filter.size()));
        return new HistoryPage(result.getContent().stream().map(ErrorHistoryView::from).toList(),
                result.getTotalElements(),result.getTotalPages(),filter.page(),filter.size(),elapsed(started));
    }
    @Transactional(readOnly=true)
    public ErrorHistoryDetail detail(long id,String projectId) {
        long started=System.nanoTime();
        String project=scope.require(projectId);
        var history=repository.findByIdAndProjectId(id,project)
                .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"History not found for project"));
        var audit=verifications.findByErrorHistoryIdOrderByChangedAtAscIdAsc(id).stream()
                .map(ErrorHistoryVerificationView::from).toList();
        return new ErrorHistoryDetail(ErrorHistoryView.from(history),audit,elapsed(started));
    }
    @Transactional
    public ErrorHistoryView changeStatus(long id,ErrorHistoryStatusRequest request) {
        return changeStatusWithAudit(id,request).history();
    }
    @Transactional
    public StatusChangeResult changeStatusWithAudit(long id,ErrorHistoryStatusRequest request) {
        long started=System.nanoTime();
        String project=scope.require(request.projectId());
        var h=repository.findByIdAndProjectId(id,project)
                .orElseThrow(()->new ResponseStatusException(NOT_FOUND,"History not found for project"));
        if(request.expectedVersion()==null || h.version!=request.expectedVersion())
            throw new ResponseStatusException(CONFLICT,"History version changed");
        ErrorStatus expected=nextStatus(h.status);
        if(request.status()!=expected)
            throw new ResponseStatusException(BAD_REQUEST,"Allowed transition is "+h.status+" -> "+expected);
        if(request.verificationNote()==null || request.verificationNote().isBlank())
            throw new ResponseStatusException(BAD_REQUEST,"Verification note is required");
        if(request.status()!=ErrorStatus.UNVERIFIED && (request.rootCause()==null || request.rootCause().isBlank()))
            throw new ResponseStatusException(BAD_REQUEST,"Explicit verified cause is required");
        if(request.status()==ErrorStatus.RESOLVED && (request.solution()==null || request.solution().isBlank()))
            throw new ResponseStatusException(BAD_REQUEST,"Explicit confirmed solution is required");
        ErrorStatus from=h.status;
        long previousVersion=h.version;
        Instant changedAt=Instant.now();
        h.status=request.status(); h.rootCause=redactor.redact(request.rootCause());
        h.solution=redactor.redact(request.solution()); h.verificationNote=redactor.redact(request.verificationNote());
        h.verifiedBy="LOCAL_USER_ATTESTATION"; h.statusChangedAt=changedAt;
        repository.flush();
        var audit=new ErrorHistoryVerification();
        audit.errorHistoryId=h.id; audit.fromStatus=from; audit.toStatus=h.status;
        audit.rootCause=h.rootCause; audit.solution=h.solution; audit.verificationNote=h.verificationNote;
        audit.actor=h.verifiedBy; audit.changedAt=changedAt; audit.previousVersion=previousVersion;
        long auditStarted=System.nanoTime();
        verifications.saveAndFlush(audit);
        long auditMillis=elapsed(auditStarted);
        embeddingIndexer.index(h);
        return new StatusChangeResult(ErrorHistoryView.from(h),ErrorHistoryVerificationView.from(audit),
                elapsed(started),auditMillis);
    }
    private ErrorStatus nextStatus(ErrorStatus current) {
        return switch(current) {
            case UNVERIFIED -> ErrorStatus.VERIFIED;
            case VERIFIED -> ErrorStatus.RESOLVED;
            case RESOLVED -> throw new ResponseStatusException(BAD_REQUEST,"RESOLVED is terminal");
        };
    }
    private void validateRange(Instant from,Instant to,String name) {
        if(from!=null && to!=null && from.isAfter(to))
            throw new ResponseStatusException(BAD_REQUEST,"Invalid "+name+" range");
    }
    private String normalized(String value,int max,String name) {
        if(value==null || value.isBlank()) return null;
        String result=value.trim();
        if(result.length()>max) throw new ResponseStatusException(BAD_REQUEST,name+" is too long");
        return result;
    }
    private String normalizedFile(String value) {
        String path=normalized(value,1024,"relatedFile");
        if(path==null) return null;
        path=path.replace('\\','/');
        if(path.startsWith("/") || path.contains(":") || Arrays.stream(path.split("/",-1))
                .anyMatch(part->part.isBlank() || part.equals(".") || part.equals("..")))
            throw new ResponseStatusException(BAD_REQUEST,"Invalid relatedFile");
        return path;
    }
    private String escapeLike(String value) {
        return value.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
    }
    private long elapsed(long started) { return (System.nanoTime()-started)/1_000_000; }
}
