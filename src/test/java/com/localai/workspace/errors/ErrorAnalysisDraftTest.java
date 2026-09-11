package com.localai.workspace.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.localai.workspace.agent.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class ErrorAnalysisDraftTest {
    private ErrorAnalysisService service(Duration ttl) throws Exception {
        var scope=mock(ErrorProjectScope.class);
        when(scope.require("project")).thenReturn("project");
        var agent=mock(AgentChatService.class);
        var json=new ObjectMapper().findAndRegisterModules();
        var response=new AgentChatResponse("project","query","unverified",List.of(),0,0,0,
                AgentChatStatus.SUCCESS,List.of(),List.of(),0,List.of(),List.of(),List.of());
        when(agent.analyzeError(any())).thenReturn(new AgentAnalysisRun(response,List.of(
                new ToolEvidence(1,"searchLogs",Instant.now(),json.readTree(
                        "{\"projectId\":\"project\",\"status\":\"SUCCESS\",\"entries\":[]}")))));
        return new ErrorAnalysisService(agent,scope,new LogSecretRedactor(),json,ttl,1);
    }

    @Test void serverSnapshotRejectsClientMutationOtherProjectAndEvictedId() throws Exception {
        var service=service(Duration.ofMinutes(30));
        var first=service.analyzeError(new ErrorAnalysisRequest("project","query"));
        ((ObjectNode)first.confirmedEvidence().get(0).result()).put("tampered","client");
        assertThat(service.draft(first.analysisId(),"project").confirmedEvidence().get(0).result().has("tampered")).isFalse();
        assertThatThrownBy(()->service.draft(first.analysisId(),"other"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var second=service.analyzeError(new ErrorAnalysisRequest("project","query"));
        assertThat(service.draft(second.analysisId(),"project").status()).isEqualTo(ErrorStatus.UNVERIFIED);
        assertThatThrownBy(()->service.draft(first.analysisId(),"project"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test void expiredDraftCannotBeSaved() throws Exception {
        var service=service(Duration.ofNanos(1));
        var result=service.analyzeError(new ErrorAnalysisRequest("project","query"));
        assertThatThrownBy(()->service.draft(result.analysisId(),"project"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
