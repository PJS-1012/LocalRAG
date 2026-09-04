package com.localai.workspace.index;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectIndexController.class)
class ProjectIndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectIndexService indexService;

    @Test
    void indexesOneProjectWithoutReturningVectors() throws Exception {
        when(indexService.index("Local_Ai_Work")).thenReturn(new ProjectIndexResult(
                "Local_Ai_Work",
                100,
                155,
                155,
                155,
                155,
                0,
                0,
                1024,
                "qwen3-embedding:0.6b",
                100,
                7000,
                50,
                7150,
                ProjectIndexStatus.SUCCESS,
                null
        ));

        mockMvc.perform(post("/api/workspaces/projects/index")
                        .param("projectId", "Local_Ai_Work"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value("Local_Ai_Work"))
                .andExpect(jsonPath("$.storedCount").value(155))
                .andExpect(jsonPath("$.dimensions").value(1024))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.vector").doesNotExist());

        verify(indexService).index("Local_Ai_Work");
    }

    @Test
    void returnsReadOnlyProjectIndexStats() throws Exception {
        Instant indexedAt = Instant.parse("2026-09-04T00:00:00Z");
        when(indexService.stats("Local_Ai_Work")).thenReturn(new ProjectIndexStats(
                "Local_Ai_Work",
                155,
                "qwen3-embedding:0.6b",
                1024,
                indexedAt
        ));

        mockMvc.perform(get("/api/workspaces/projects/index/stats")
                        .param("projectId", "Local_Ai_Work"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storedChunkCount").value(155))
                .andExpect(jsonPath("$.embeddingModel").value("qwen3-embedding:0.6b"))
                .andExpect(jsonPath("$.dimensions").value(1024))
                .andExpect(jsonPath("$.latestIndexedAt").value("2026-09-04T00:00:00Z"));

        verify(indexService).stats("Local_Ai_Work");
    }
}
