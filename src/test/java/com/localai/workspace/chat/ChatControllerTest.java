package com.localai.workspace.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    void returnsModelAnswer() throws Exception {
        when(chatService.chat("Java 17의 장점을 한 문장으로 설명해줘."))
                .thenReturn("Java 17은 장기 지원 버전입니다.");

        mockMvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content("""
                                {"message":"Java 17의 장점을 한 문장으로 설명해줘."}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "answer":"Java 17은 장기 지원 버전입니다.",
                          "model":"qwen3:8b"
                        }
                        """));

        verify(chatService).chat("Java 17의 장점을 한 문장으로 설명해줘.");
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType("application/json")
                        .content("""
                                {"message":" "}
                                """))
                .andExpect(status().isBadRequest());
    }
}
