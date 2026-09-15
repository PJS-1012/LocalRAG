package com.localai.workspace.overview;

import com.localai.workspace.agent.*;
import com.localai.workspace.automation.*;
import com.localai.workspace.discovery.*;
import com.localai.workspace.errors.ErrorHistoryQueryService;
import com.localai.workspace.index.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProjectOverviewServiceTest {
    @Test void partialEnvironmentFailureDoesNotDiscardCheapDashboardEvidence() {
        ProjectDiscoveryService projects=mock(ProjectDiscoveryService.class);
        ProjectIndexService indexes=mock(ProjectIndexService.class);
        GitReadOnlyService git=mock(GitReadOnlyService.class);
        DockerReadOnlyService docker=mock(DockerReadOnlyService.class);
        OllamaReadOnlyService ollama=mock(OllamaReadOnlyService.class);
        DatabaseReadOnlyService database=mock(DatabaseReadOnlyService.class);
        ErrorHistoryQueryService errors=mock(ErrorHistoryQueryService.class);
        AutomationHistoryService automation=mock(AutomationHistoryService.class);
        NotificationCandidateService notifications=mock(NotificationCandidateService.class);
        var project=new DetectedProject("P","P",Path.of("C:/workspace/P"),ProjectType.JAVA,
                "Spring Boot",true,true,List.of());
        when(projects.discoverProjects()).thenReturn(List.of(project));
        when(indexes.stats("P")).thenReturn(new ProjectIndexStats("P",10,"qwen",1024,Instant.EPOCH));
        when(git.getStatus("P")).thenReturn(new GitStatusResult("P",GitToolStatus.SUCCESS,"main",true,
                List.of(),List.of(),List.of(),List.of(),null));
        when(git.getRecentCommits("P",5)).thenReturn(new GitRecentCommitsResult("P",GitToolStatus.SUCCESS,
                5,5,List.of(),null));
        when(docker.getStatus()).thenThrow(new IllegalStateException("offline"));
        when(docker.getProjectContainers("P")).thenReturn(new ProjectContainerStatusResult("P",
                LocalEnvironmentStatus.NOT_CONFIGURED,null,0,List.of(),null));
        when(ollama.getStatus()).thenReturn(new OllamaStatusResult(LocalEnvironmentStatus.AVAILABLE,true,1,List.of(),null));
        when(database.getStatus()).thenReturn(new DatabaseStatusResult(LocalEnvironmentStatus.AVAILABLE,true,true,null));
        when(errors.automationState("P")).thenReturn(new ErrorHistoryQueryService.AutomationState(3,1,1,1,3L,0,Instant.EPOCH));
        when(automation.list("P",0,1)).thenReturn(new AutomationPage<>(List.of(),0,0,0,1));
        when(notifications.list("P",0,100)).thenReturn(new AutomationPage<>(List.of(),0,0,0,100));
        var service=new ProjectOverviewService(projects,indexes,git,docker,ollama,database,errors,automation,notifications);

        ProjectOverview result=service.get("P");

        assertThat(result.project()).isEqualTo(project);
        assertThat(result.index().storedChunkCount()).isEqualTo(10);
        assertThat(result.database().pgvectorAvailable()).isTrue();
        assertThat(result.docker()).isNull();
        assertThat(result.warnings()).containsExactly("Docker status unavailable");
    }
}
