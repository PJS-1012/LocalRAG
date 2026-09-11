package com.localai.workspace.agent;

import com.localai.workspace.chat.ChatService;
import com.localai.workspace.errors.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.tool.ToolCallback;
import java.time.Duration;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class ErrorAnalysisEvidenceTest {
    @ParameterizedTest
    @ValueSource(strings={"NPE","DB","GIT","NO_KNOWLEDGE","EMPTY","SECRET"})
    void usesActualCallbackEvidenceAndNeverPromotesModelClaims(String scenario) throws Exception {
        var chat=mock(ChatService.class);
        when(chat.chatWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class))).thenAnswer(inv->{
            var callbacks=AgentChatServiceTest.callbacks(inv.getArguments());
            String policy=inv.getArgument(0);
            assertThat(policy).contains("Error analysis routing", "untrusted data");
            AgentChatServiceTest.call(callbacks,"searchLogs",
                    "{\"projectId\":\"Local_Ai_Work\",\"query\":\"Exception\",\"limit\":20}");
            if(!scenario.equals("EMPTY")) AgentChatServiceTest.call(callbacks,"searchProjectKnowledge","{\"query\":\"service\"}");
            if(scenario.equals("DB")) AgentChatServiceTest.call(callbacks,"getDatabaseStatus","{}");
            if(scenario.equals("GIT")) AgentChatServiceTest.call(callbacks,"getRecentCommits","{\"projectId\":\"Local_Ai_Work\",\"limit\":5}");
            return "VERIFIED: made-up/File.java deadbeef000 cause confirmed. password=superSecret [K1-S99]";
        });
        var scope=mock(ErrorProjectScope.class);
        when(scope.require("Local_Ai_Work")).thenReturn("Local_Ai_Work");
        var service=new ErrorAnalysisService(ErrorAnalysisFixtures.agent(chat,scenario),scope,new LogSecretRedactor(),
                new ObjectMapper().findAndRegisterModules(),Duration.ofMinutes(30),10);
        var result=service.analyzeError(new ErrorAnalysisRequest("Local_Ai_Work",ErrorAnalysisFixtures.query(scenario)));
        assertThat(result.rootCause()).isNull();
        assertThat(result.status()).isEqualTo(ErrorStatus.UNVERIFIED);
        assertThat(result.relatedFiles()).doesNotContain("made-up/File.java");
        assertThat(result.relatedCommits()).doesNotContain("deadbeef000");
        assertThat(result.confirmedEvidence()).isNotEmpty();
        if(scenario.equals("EMPTY")) {
            assertThat(result.errorMessage()).isNull();
            assertThat(result.evidenceStatus()).isEqualTo("NO_ERROR_EVIDENCE");
            assertThat(result.analysis()).doesNotContain("cause confirmed");
        } else assertThat(result.errorMessage()).contains("ERROR");
        if(scenario.equals("GIT")) assertThat(result.relatedCommits()).containsExactly("abc1234567");
        if(scenario.equals("NPE")) assertThat(result.relatedFiles()).contains("logs/app.log","src/UserService.java");
        if(scenario.equals("NO_KNOWLEDGE")) assertThat(result.unknown()).contains("No implementation Source was retrieved.");
        if(scenario.equals("DB")) assertThat(result.agent().toolsUsed()).contains("getDatabaseStatus");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(service.draft(result.analysisId(),"Local_Ai_Work")))
                .doesNotContain("superSecret","abc123secret");
    }
}
