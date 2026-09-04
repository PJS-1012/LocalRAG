package com.localai.workspace.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentChatController.class)
class AgentChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentChatService agentChatService;

    @Test
    void returnsAgentAnswerAndToolMetadata() throws Exception {
        AgentChatRequest request = new AgentChatRequest("Local_Ai_Work", "현재 Git 상태 알려줘");
        when(agentChatService.chat(request)).thenReturn(new AgentChatResponse(
                request.projectId(), request.query(), "main 브랜치이며 깨끗합니다.",
                List.of("getGitStatus"), 12, 1500, 1512, AgentChatStatus.SUCCESS, List.of()
        ));

        mockMvc.perform(post("/api/workspaces/projects/agent/chat")
                        .contentType("application/json")
                        .content("""
                                {"projectId":"Local_Ai_Work","query":"현재 Git 상태 알려줘"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.toolsUsed[0]").value("getGitStatus"))
                .andExpect(jsonPath("$.toolExecutionDurationMillis").value(12));

        verify(agentChatService).chat(request);
    }

    @Test
    void rejectsBlankProjectOrQuery() throws Exception {
        mockMvc.perform(post("/api/workspaces/projects/agent/chat")
                        .contentType("application/json")
                        .content("{\"projectId\":\" \",\"query\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
}
