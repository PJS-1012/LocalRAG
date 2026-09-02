package com.localai.workspace.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmbeddingController.class)
class EmbeddingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmbeddingService embeddingService;

    @Test
    void returnsEmbeddingSummary() throws Exception {
        when(embeddingService.embed("예약 생성 로직"))
                .thenReturn(new EmbeddingService.EmbeddingResult(1024, List.of(0.1f, -0.2f)));

        mockMvc.perform(post("/api/embeddings")
                        .contentType("application/json")
                        .content("""
                                {"text":"예약 생성 로직"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "model":"qwen3-embedding:0.6b",
                          "dimensions":1024,
                          "preview":[0.1,-0.2]
                        }
                        """));

        verify(embeddingService).embed("예약 생성 로직");
    }

    @Test
    void rejectsBlankText() throws Exception {
        mockMvc.perform(post("/api/embeddings")
                        .contentType("application/json")
                        .content("""
                                {"text":" "}
                                """))
                .andExpect(status().isBadRequest());
    }
}
