package com.localai.workspace.automation;

import com.localai.workspace.agent.*;
import com.localai.workspace.errors.ErrorProjectScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EnvironmentWatchServiceTest {
    @Test void unavailableDatabaseIsReportedWithoutAnyRecoveryAction() {
        ErrorProjectScope scope=mock(ErrorProjectScope.class);
        DockerReadOnlyService docker=mock(DockerReadOnlyService.class);
        OllamaReadOnlyService ollama=mock(OllamaReadOnlyService.class);
        DatabaseReadOnlyService database=mock(DatabaseReadOnlyService.class);
        when(scope.require("P")).thenReturn("P");
        when(docker.getStatus()).thenReturn(new DockerStatusResult(LocalEnvironmentStatus.AVAILABLE,true,true,"1",null));
        when(docker.getProjectContainers("P")).thenReturn(new ProjectContainerStatusResult("P",
                LocalEnvironmentStatus.NOT_CONFIGURED,null,0,List.of(),null));
        when(ollama.getStatus()).thenReturn(new OllamaStatusResult(LocalEnvironmentStatus.AVAILABLE,true,2,List.of("qwen"),null));
        when(database.getStatus()).thenReturn(new DatabaseStatusResult(LocalEnvironmentStatus.NOT_RUNNING,false,false,"connection refused"));
        var service=new EnvironmentWatchService(scope,docker,ollama,database,new LogSecretRedactor());

        EnvironmentWatchResult result=service.watch("P");

        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(result.observations()).extracting(EnvironmentObservation::component)
                .containsExactly("DOCKER","PROJECT_CONTAINERS","OLLAMA","DATABASE");
        verify(docker).getStatus();
        verify(docker).getProjectContainers("P");
        verify(ollama).getStatus();
        verify(database).getStatus();
    }
}
