package com.localai.workspace.workflow;

import com.localai.workspace.agent.*;
import com.localai.workspace.chat.ChatService;
import com.localai.workspace.errors.*;
import com.localai.workspace.rag.*;
import com.localai.workspace.search.ProjectSemanticSearchStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeveloperWorkflowServiceTest {
    final ErrorProjectScope scope=mock(ErrorProjectScope.class);
    final GitReadOnlyService git=mock(GitReadOnlyService.class);
    final RagContextAssemblyService rag=mock(RagContextAssemblyService.class);
    final ErrorHistoryQueryService histories=mock(ErrorHistoryQueryService.class);
    final ChatService chat=mock(ChatService.class);
    final LogSecretRedactor redactor=new LogSecretRedactor();

    @Test void progressUsesBoundedEvidenceAndFlagsOutdatedReadmeWithoutPercentage() {
        when(scope.require("Local_Ai_Work")).thenReturn("Local_Ai_Work");
        when(git.getStatus("Local_Ai_Work")).thenReturn(status(false,List.of("src/main/java/App.java")));
        when(git.getRecentCommits("Local_Ai_Work",5)).thenReturn(new GitRecentCommitsResult("Local_Ai_Work",
                GitToolStatus.SUCCESS,5,1,List.of(new RecentCommit("abcdef123456","Add Error History","dev",
                "2026-09-10T00:00:00Z")),null));
        when(git.getDiffSummary("Local_Ai_Work")).thenReturn(new GitDiffSummaryResult("Local_Ai_Work",
                GitToolStatus.SUCCESS,1,10,2,List.of(new GitDiffFileSummary("src/main/java/App.java",10,2,false)),
                List.of(),null));
        when(histories.unresolved("Local_Ai_Work")).thenReturn(
                new ErrorHistoryQueryService.UnresolvedResult(0,List.of()));
        when(rag.assemble(any())).thenReturn(context(List.of(
                source("README.md","Phase 6 complete"),
                source("docs/decisions/0027.md","Phase 8 current"),
                source("src/main/java/App.java","class App {}"))));
        when(chat.chat(anyString(),anyString())).thenReturn("근거 기준으로 Phase 8 변경이 진행 중입니다.");

        var result=new ProjectProgressService(scope,git,rag,histories,chat,redactor)
                .analyze(new ProjectProgressRequest("Local_Ai_Work"));

        assertThat(result.completed()).singleElement().asString().contains("abcdef1234","Add Error History");
        assertThat(result.inProgress()).singleElement().asString().contains("uncommitted");
        assertThat(result.documentationMismatch()).anyMatch(value->value.contains("README") && value.contains("Phase 6"));
        assertThat(result.unknown()).anyMatch(value->value.contains("test"));
        assertThat(result.summary()).doesNotContain("87%");
        verify(rag,times(2)).assemble(any());
    }

    @Test void recentActivityFiltersByCommitTimestampAndSeparatesWorkingTree() {
        when(scope.require("Local_Ai_Work")).thenReturn("Local_Ai_Work");
        when(git.getRecentCommits("Local_Ai_Work",20)).thenReturn(new GitRecentCommitsResult("Local_Ai_Work",
                GitToolStatus.SUCCESS,20,2,List.of(
                new RecentCommit("new1234567","Add audit","dev","2026-09-10T01:00:00Z"),
                new RecentCommit("old1234567","Old work","dev","2026-08-01T00:00:00Z")),null));
        when(git.getStatus("Local_Ai_Work")).thenReturn(status(false,List.of("docs/notes.md")));
        when(git.getDiffSummary("Local_Ai_Work")).thenReturn(new GitDiffSummaryResult("Local_Ai_Work",
                GitToolStatus.SUCCESS,1,2,0,List.of(new GitDiffFileSummary("docs/notes.md",2,0,false)),List.of(),null));
        when(rag.assemble(any())).thenReturn(context(List.of(source("docs/decisions/0027.md","Audit decision"))));
        when(chat.chat(anyString(),anyString())).thenReturn("최근 감사 이력 변경과 문서 수정이 관찰됩니다.");

        var result=new DevelopmentActivityService(scope,git,rag,chat,redactor).summarize(
                new DevelopmentActivityRequest("Local_Ai_Work",Instant.parse("2026-09-01T00:00:00Z"),null));

        assertThat(result.commits()).extracting(RecentCommit::hash).containsExactly("new1234567");
        assertThat(result.changedAreas()).contains("documentation");
        assertThat(result.relatedDecisionLogs()).singleElement().satisfies(e->assertThat(e.sourcePath()).contains("0027"));
        assertThat(result.currentWorkingTree().clean()).isFalse();
        assertThat(result.commitLimit()).isEqualTo(20);
    }

    private GitStatusResult status(boolean clean,List<String> modified) {
        return new GitStatusResult("Local_Ai_Work",GitToolStatus.SUCCESS,"main",clean,modified,
                List.of(),List.of(),List.of(),null);
    }
    private RagContextSource source(String path,String content) {
        return new RagContextSource("S1",1,"c","Local_Ai_Work",path,
                path.substring(path.lastIndexOf('/')+1),"md",0,1,3,content,.8,"fixture");
    }
    private RagContextAssemblyResult context(List<RagContextSource> sources) {
        return new RagContextAssemblyResult("Local_Ai_Work","q",8000,sources.size(),sources.size(),0,100,
                ProjectSemanticSearchStatus.SUCCESS,RagContextAssemblyStatus.SUCCESS,null,sources,"context");
    }
}
