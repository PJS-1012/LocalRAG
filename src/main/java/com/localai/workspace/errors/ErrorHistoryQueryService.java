package com.localai.workspace.errors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ErrorHistoryQueryService {
    public record UnresolvedResult(long total, List<ErrorHistoryView> items) { }
    private final ErrorHistoryRepository repository;
    private final ErrorProjectScope scope;

    public ErrorHistoryQueryService(ErrorHistoryRepository repository, ErrorProjectScope scope) {
        this.repository=repository; this.scope=scope;
    }

    @Transactional(readOnly=true)
    public UnresolvedResult unresolved(String projectId) {
        String project=scope.require(projectId);
        long total=repository.countByProjectIdAndStatus(project,ErrorStatus.UNVERIFIED)
                +repository.countByProjectIdAndStatus(project,ErrorStatus.VERIFIED);
        List<ErrorHistory> histories=new ArrayList<>();
        histories.addAll(repository.findTop5ByProjectIdAndStatusOrderByRecordedAtDesc(project,ErrorStatus.UNVERIFIED));
        histories.addAll(repository.findTop5ByProjectIdAndStatusOrderByRecordedAtDesc(project,ErrorStatus.VERIFIED));
        List<ErrorHistoryView> views=histories.stream()
                .sorted((a,b)->b.recordedAt.compareTo(a.recordedAt)).limit(5).map(ErrorHistoryView::from).toList();
        return new UnresolvedResult(total,views);
    }
}
