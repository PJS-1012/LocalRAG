package com.localai.workspace.agent;
import com.localai.workspace.discovery.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
class DashboardGitServiceTest {
    private final GitProcessRunner runner=mock(GitProcessRunner.class);
    private final Path root=Path.of("C:/workspace/P");
    private final String local="a".repeat(40), upstream="b".repeat(40);
    private final DetectedProject project=new DetectedProject("P","P",root,ProjectType.JAVA,null,true,true,List.of());
    private GitCommandResult ok(String text){return new GitCommandResult(0,text,false,false);}
    @Test void cleanDoesNotMeanPushedAndAncestryUsesConfiguredUpstream() {
        when(runner.status(root)).thenReturn(ok("## main...company/release [ahead 1, behind 2]\n"));
        when(runner.upstream(root)).thenReturn(ok(upstream));
        when(runner.divergence(root,upstream)).thenReturn(ok("1\t2"));
        when(runner.recentCommits(root,5)).thenReturn(ok(local+"\u001fLocal change\u001fAuthor\u001f2026-09-17\u001e"+upstream+"\u001fShared\u001fAuthor\u001f2026-09-16\u001e"));
        when(runner.isAncestor(root,local,upstream)).thenReturn(new GitCommandResult(1,"",false,false));
        when(runner.isAncestor(root,upstream,upstream)).thenReturn(ok(""));
        var result=new DashboardGitService(runner).read(project);
        assertThat(result.clean()).isTrue();
        assertThat(result.upstream()).isEqualTo("company/release");
        assertThat(result.ahead()).isEqualTo(1);assertThat(result.behind()).isEqualTo(2);
        assertThat(result.commits()).extracting(DashboardGitService.Commit::pushStatus).containsExactly("UNPUSHED","PUSHED");
    }
    @Test void noUpstreamIsNotGuessedAsUnpushed() {
        when(runner.status(root)).thenReturn(ok("## main\n?? new.txt\n"));
        when(runner.recentCommits(root,5)).thenReturn(ok(local+"\u001fm\u001fa\u001ft"));
        var result=new DashboardGitService(runner).read(project);
        assertThat(result.clean()).isFalse();assertThat(result.remoteStatus()).isEqualTo("NO_UPSTREAM");
        assertThat(result.ahead()).isNull();assertThat(result.commits().get(0).pushStatus()).isEqualTo("NO_UPSTREAM");
        verify(runner,never()).upstream(any());
    }
    @Test void missingTrackingRefIsUnavailableNotZeroAhead() {
        when(runner.status(root)).thenReturn(ok("## main...origin/main [gone]"));
        when(runner.upstream(root)).thenReturn(new GitCommandResult(128,"",false,false));
        when(runner.recentCommits(root,5)).thenReturn(ok(""));
        var result=new DashboardGitService(runner).read(project);
        assertThat(result.remoteStatus()).isEqualTo("UNAVAILABLE");assertThat(result.ahead()).isNull();
    }
    @Test void workingChangesAreCountedWithoutExtraProcessCalls() {
        when(runner.status(root)).thenReturn(ok("## main\n M a.java\n?? b.java\nA  c.java\n D d.java\nR  a -> b\n"));
        when(runner.recentCommits(root,5)).thenReturn(ok(""));
        assertThat(new DashboardGitService(runner).read(project).changes()).isEqualTo(new DashboardGitService.Changes(2,2,1));
        verify(runner,times(1)).status(root);verify(runner,times(1)).recentCommits(root,5);
        verifyNoMoreInteractions(runner);
    }
    @Test void unbornBranchRetainsItsActualName() {
        when(runner.status(root)).thenReturn(ok("## No commits yet on master\n?? readme.md"));
        when(runner.recentCommits(root,5)).thenReturn(new GitCommandResult(128,"",false,false));
        var result=new DashboardGitService(runner).read(project);
        assertThat(result.branch()).isEqualTo("master");
        assertThat(result.remoteStatus()).isEqualTo("NO_UPSTREAM");
        assertThat(result.commits()).isEmpty();
    }
}
