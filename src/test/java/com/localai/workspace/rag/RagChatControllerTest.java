package com.localai.workspace.rag;

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

@WebMvcTest(RagChatController.class)
class RagChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagChatService ragChatService;

    @Test
    void returnsRagAnswerAndCitationMetadata() throws Exception {
        RagChatRequest request = new RagChatRequest("Local_Ai_Work", "프로젝트 타입은?");
        when(ragChatService.chat(request)).thenReturn(new RagChatResponse(
                "Local_Ai_Work", "프로젝트 타입은?", "탐지합니다 [S1].", 1,
                List.of(new RagChatSource("S1", "src/Detector.java", "Detector.java", 1, 20)),
                500, 30, 2000, 2030, RagChatStatus.SUCCESS,
                List.of("S1"), List.of(), List.of()
        ));

        mockMvc.perform(post("/api/workspaces/projects/rag/chat")
                        .contentType("application/json")
                        .content("""
                                {
                                  "projectId": "Local_Ai_Work",
                                  "query": "프로젝트 타입은?"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.answer").value("탐지합니다 [S1]."))
                .andExpect(jsonPath("$.sources[0].id").value("S1"))
                .andExpect(jsonPath("$.sources[0].filePath").value("src/Detector.java"))
                .andExpect(jsonPath("$.usedSourceIds[0]").value("S1"))
                .andExpect(jsonPath("$.context").doesNotExist());

        verify(ragChatService).chat(request);
    }

    @Test
    void rejectsBlankProjectOrQuery() throws Exception {
        mockMvc.perform(post("/api/workspaces/projects/rag/chat")
                        .contentType("application/json")
                        .content("""
                                {"projectId":" ","query":" "}
                                """))
                .andExpect(status().isBadRequest());
    }
}
