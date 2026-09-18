package com.localai.workspace.overview;
import com.localai.workspace.agent.DashboardGitService;
import com.localai.workspace.discovery.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
class WorkspaceOverviewServiceTest {
    @Test void aggregatesAllProjectsWithFourBulkQueriesAndIsolatesGitFailure() {
        var discovery=mock(ProjectDiscoveryService.class);var git=mock(DashboardGitService.class);var jdbc=mock(JdbcTemplate.class);
        var p=new DetectedProject("P","P",Path.of("C:/workspace/P"),ProjectType.JAVA,null,true,true,List.of());
        var q=new DetectedProject("Q","Q",Path.of("C:/workspace/Q"),ProjectType.JAVA,null,false,true,List.of());
        when(discovery.discoverProjects()).thenReturn(List.of(p,q));
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        when(jdbc.queryForList(contains("document_chunk_embedding"))).thenReturn(List.of(Map.of("project_id","P","documents",2L,"chunks",7L)));
        when(git.read(p)).thenThrow(new IllegalStateException("offline"));
        var result=new WorkspaceOverviewService(discovery,git,jdbc).get();
        assertThat(result.projects()).hasSize(2);
        assertThat(result.projects().get(0).indexedDocumentCount()).isEqualTo(2);
        assertThat(result.projects().get(0).indexedChunkCount()).isEqualTo(7);
        assertThat(result.projects().get(0).warnings()).contains("Git metadata unavailable");
        assertThat(result.projects().get(1).indexedChunkCount()).isZero();
        verify(jdbc,times(4)).queryForList(anyString());verify(discovery).discoverProjects();
    }
    @Test void databaseFailureIsUnknownRatherThanFalseZero() {
        var discovery=mock(ProjectDiscoveryService.class);var git=mock(DashboardGitService.class);var jdbc=mock(JdbcTemplate.class);
        when(discovery.discoverProjects()).thenReturn(List.of(new DetectedProject("P","P",Path.of("P"),ProjectType.JAVA,null,false,true,List.of())));
        when(jdbc.queryForList(anyString())).thenThrow(new IllegalStateException());
        var p=new WorkspaceOverviewService(discovery,git,jdbc).get().projects().get(0);
        assertThat(p.indexedChunkCount()).isNull();assertThat(p.automationStatus()).isEqualTo("UNKNOWN");
        assertThat(p.warnings()).hasSize(4);
    }
}
