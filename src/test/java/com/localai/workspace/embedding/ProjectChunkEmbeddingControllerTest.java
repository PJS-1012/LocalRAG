package com.localai.workspace.embedding;

import com.localai.workspace.chunk.DocumentChunk;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectChunkEmbeddingController.class)
class ProjectChunkEmbeddingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectChunkEmbeddingService projectEmbeddingService;

    @MockitoBean
    private EmbeddingService embeddingService;

    @Test
    void returnsOnlyLimitedVectorPreviews() throws Exception {
        DocumentChunk chunk = chunk();
        float[] vector = new float[1024];
        EmbeddedChunk embeddedChunk = new EmbeddedChunk(
                chunk,
                "qwen3-embedding:0.6b",
                1024,
                vector
        );
        ProjectChunkEmbeddingResult result = new ProjectChunkEmbeddingResult(
                "Local_Ai_Work",
                1,
                1,
                1,
                0,
                0,
                0,
                "qwen3-embedding:0.6b",
                1024,
                EmbeddingRunStatus.SUCCESS,
                null,
                12,
                30,
                30.0,
                42,
                List.of(ChunkEmbeddingOutcome.success(embeddedChunk))
        );
        when(projectEmbeddingService.embed("Local_Ai_Work")).thenReturn(result);
        when(embeddingService.preview(vector))
                .thenReturn(List.of(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f));

        mockMvc.perform(post("/api/workspaces/projects/chunks/embeddings/preview")
                        .param("projectId", "Local_Ai_Work"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value("Local_Ai_Work"))
                .andExpect(jsonPath("$.chunkCount").value(1))
                .andExpect(jsonPath("$.embeddedCount").value(1))
                .andExpect(jsonPath("$.dimensions").value(1024))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.chunks[0].vectorPreview.length()").value(8))
                .andExpect(jsonPath("$.chunks[0].vector").doesNotExist())
                .andExpect(jsonPath("$.failures").isEmpty());

        verify(projectEmbeddingService).embed("Local_Ai_Work");
        verify(embeddingService).preview(vector);
    }

    private DocumentChunk chunk() {
        return new DocumentChunk(
                "chunk-0",
                "Local_Ai_Work",
                "README.md",
                "README.md",
                "md",
                0,
                "# LocalRAG",
                0,
                10,
                1,
                1,
                "fingerprint",
                Instant.parse("2026-09-04T00:00:00Z")
        );
    }
}
