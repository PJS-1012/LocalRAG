package com.localai.workspace.agent;

import com.localai.workspace.chat.ChatService;
import com.localai.workspace.errors.*;
import com.localai.workspace.rag.*;
import com.localai.workspace.workflow.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeveloperWorkflowAgentToolsTest {
    @Test void registersAndRoutesEachWorkflowToolWithoutCallingUnrelatedService() {
        ChatService chat=mock(ChatService.class);
        ErrorSimilarityService similar=mock(ErrorSimilarityService.class);
        ProjectProgressService progress=mock(ProjectProgressService.class);
        DevelopmentActivityService activity=mock(DevelopmentActivityService.class);
        when(similar.find(any())).thenReturn(new ErrorSimilarityResponse("SUCCESS","Local_Ai_Work",5,.65,0,
                1,1,2,"Similarity requires separate verification",List.of()));
        when(progress.analyze(any())).thenReturn(progressResult());
        when(activity.summarize(any())).thenReturn(activityResult());
        AgentChatService agent=agent(chat,similar,progress,activity);
        when(chat.chatWithToolCallbacks(anyString(),anyString(),any(ToolCallback[].class))).thenAnswer(inv->{
            String prompt=inv.getArgument(1);
            ToolCallback[] callbacks=AgentChatServiceTest.callbacks(inv.getArguments());
            assertThat(callbacks).hasSize(15);
            if(prompt.contains("비슷한")) {
                AgentChatServiceTest.call(callbacks,"findSimilarErrors","""
                        {"projectId":"Local_Ai_Work","errorMessage":"connection refused","topK":5}
                        """);
            } else if(prompt.contains("어디까지")) {
                AgentChatServiceTest.call(callbacks,"analyzeProjectProgress","{\"projectId\":\"Local_Ai_Work\"}");
            } else {
                AgentChatServiceTest.call(callbacks,"summarizeRecentDevelopment","""
                        {"projectId":"Local_Ai_Work","timeRange":"RECENT","commitLimit":5}
                        """);
            }
            return "근거 기반 응답";
        });

        assertThat(agent.chat(new AgentChatRequest("Local_Ai_Work","이 오류 예전에 비슷한 거 있었어?")).toolsUsed())
                .containsExactly("findSimilarErrors");
        verify(similar).find(any()); verifyNoInteractions(progress,activity);
        clearInvocations(similar,progress,activity);
        assertThat(agent.chat(new AgentChatRequest("Local_Ai_Work","LocalRAG 지금 어디까지 했어?")).toolsUsed())
                .containsExactly("analyzeProjectProgress");
        verify(progress).analyze(any()); verifyNoInteractions(similar,activity);
        clearInvocations(similar,progress,activity);
        assertThat(agent.chat(new AgentChatRequest("Local_Ai_Work","최근 작업 요약해줘")).toolsUsed())
                .containsExactly("summarizeRecentDevelopment");
        verify(activity).summarize(any()); verifyNoInteractions(similar,progress);
    }

    @Test void toolRejectsCrossProjectArgumentBeforeBusinessService() {
        ErrorSimilarityService similar=mock(ErrorSimilarityService.class);
        var tools=new DeveloperWorkflowAgentTools("Project-A",similar,
                mock(ProjectProgressService.class),mock(DevelopmentActivityService.class));
        assertThatThrownBy(()->tools.findSimilarErrors("Project-B",null,"error",null,5))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(similar);
    }

    private AgentChatService agent(ChatService chat,ErrorSimilarityService similar,
            ProjectProgressService progress,DevelopmentActivityService activity) {
        return new AgentChatService(chat,mock(GitReadOnlyService.class),mock(DockerReadOnlyService.class),
                mock(OllamaReadOnlyService.class),mock(DatabaseReadOnlyService.class),mock(LogReadOnlyService.class),
                mock(RagContextAssemblyService.class),new LogSecretRedactor(),new RagCitationValidator(),
                similar,progress,activity);
    }
    private ProjectProgressResponse progressResult() {
        return new ProjectProgressResponse("SUCCESS","Local_Ai_Work","summary",List.of(),List.of(),List.of(),
                List.of(),List.of(),List.of(),0,List.of(),List.of(),Instant.now(),1,1,1,3);
    }
    private DevelopmentActivityResponse activityResult() {
        return new DevelopmentActivityResponse("SUCCESS","Local_Ai_Work",null,5,List.of(),List.of(),"summary",
                new GitStatusResult("Local_Ai_Work",GitToolStatus.SUCCESS,"main",true,List.of(),List.of(),List.of(),
                        List.of(),null),List.of(),Instant.now(),1,1,1,3);
    }
}
