package com.localai.workspace.automation;

import com.localai.workspace.agent.*;
import com.localai.workspace.errors.*;
import com.localai.workspace.index.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProjectStateFingerprintServiceTest {
    @Test void headAndWorkingTreeArePartOfTheCheapFingerprint() {
        ErrorProjectScope scope=mock(ErrorProjectScope.class);
        GitReadOnlyService git=mock(GitReadOnlyService.class);
        ErrorHistoryQueryService histories=mock(ErrorHistoryQueryService.class);
        ProjectIndexRepository indexes=mock(ProjectIndexRepository.class);
        when(scope.require("P")).thenReturn("P");
        when(git.getRecentCommits("P",1)).thenReturn(commits("a1"),commits("a1"),commits("b2"));
        when(git.getStatus("P")).thenReturn(status(List.of()),status(List.of("src/A.java")),status(List.of("src/A.java")));
        when(git.getDiffSummary("P")).thenReturn(diff(List.of()),diff(List.of("src/A.java")),diff(List.of("src/A.java")));
        when(histories.automationState("P")).thenReturn(new ErrorHistoryQueryService.AutomationState(0,0,0,0,null,0,null));
        when(indexes.stats("P")).thenReturn(Optional.of(new ProjectIndexStats("P",12,"qwen",1024,Instant.EPOCH)));
        var service=new ProjectStateFingerprintService(scope,git,histories,indexes);

        String clean=service.capture("P").fingerprint();
        String dirty=service.capture("P").fingerprint();
        String newHead=service.capture("P").fingerprint();

        assertThat(dirty).isNotEqualTo(clean);
        assertThat(newHead).isNotEqualTo(dirty);
    }

    private GitRecentCommitsResult commits(String hash) {
        return new GitRecentCommitsResult("P",GitToolStatus.SUCCESS,1,1,
                List.of(new RecentCommit(hash,"message","author","now")),null);
    }
    private GitStatusResult status(List<String> modified) {
        return new GitStatusResult("P",GitToolStatus.SUCCESS,"main",modified.isEmpty(),modified,List.of(),List.of(),List.of(),null);
    }
    private GitDiffSummaryResult diff(List<String> files) {
        return new GitDiffSummaryResult("P",GitToolStatus.SUCCESS,files.size(),files.size(),0,
                files.stream().map(path->new GitDiffFileSummary(path,1,0,false)).toList(),List.of(),null);
    }
}
