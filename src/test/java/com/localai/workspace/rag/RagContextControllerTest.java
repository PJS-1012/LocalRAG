package com.localai.workspace.rag;

import com.localai.workspace.search.ProjectSemanticSearchStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RagContextController.class)
class RagContextControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagContextAssemblyService contextAssemblyService;

    @Test
    void returnsFormattedContextAndSourceMetadata() throws Exception {
        RagContextPreviewRequest request = new RagContextPreviewRequest(
                "Local_Ai_Work", "프로젝트 타입 탐지"
        );
        when(contextAssemblyService.assemble(request)).thenReturn(new RagContextAssemblyResult(
                "Local_Ai_Work", "프로젝트 타입 탐지", 8000, 1, 1, 0, 138,
                ProjectSemanticSearchStatus.SUCCESS, RagContextAssemblyStatus.SUCCESS, null,
                List.of(new RagContextSource(
                        "S1", 1, "chunk-id", "Local_Ai_Work",
                        "src/ProjectTypeDetector.java", "ProjectTypeDetector.java", "java",
                        0, 1, 20, "class ProjectTypeDetector {}", 0.81,
                        "qwen3-embedding:0.6b"
                )),
                "[Source S1]\nProject: Local_Ai_Work\nFile: src/ProjectTypeDetector.java"
        ));

        mockMvc.perform(post("/api/workspaces/projects/rag/context/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "Local_Ai_Work",
                                  "query": "프로젝트 타입 탐지"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.contextBudgetCharacters").value(8000))
                .andExpect(jsonPath("$.sourceCount").value(1))
                .andExpect(jsonPath("$.excludedByBudgetCount").value(0))
                .andExpect(jsonPath("$.sources[0].citationId").value("S1"))
                .andExpect(jsonPath("$.sources[0].similarity").value(0.81))
                .andExpect(jsonPath("$.context").value(org.hamcrest.Matchers.containsString(
                        "[Source S1]"
                )));

        verify(contextAssemblyService).assemble(request);
    }
}
