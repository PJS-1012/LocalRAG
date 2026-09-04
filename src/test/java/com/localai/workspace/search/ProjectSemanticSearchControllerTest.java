package com.localai.workspace.search;

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

@WebMvcTest(ProjectSemanticSearchController.class)
class ProjectSemanticSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectSemanticSearchService searchService;

    @Test
    void returnsRankedMetadataWithoutQueryVector() throws Exception {
        ProjectSemanticSearchRequest request = new ProjectSemanticSearchRequest(
                "Local_Ai_Work",
                "프로젝트 타입 탐지",
                5,
                0.50,
                null
        );
        when(searchService.search(request)).thenReturn(new ProjectSemanticSearchResponse(
                "Local_Ai_Work",
                "프로젝트 타입 탐지",
                5,
                0.50,
                true,
                1024,
                1,
                40,
                3,
                45,
                ProjectSemanticSearchStatus.SUCCESS,
                null,
                List.of(new ProjectSemanticSearchMatch(
                        1,
                        "chunk-id",
                        "Local_Ai_Work",
                        "src/main/java/ProjectDiscoveryService.java",
                        "ProjectDiscoveryService.java",
                        "java",
                        0,
                        1,
                        20,
                        "class ProjectDiscoveryService {}",
                        0.81,
                        "qwen3-embedding:0.6b"
                ))
        ));

        mockMvc.perform(post("/api/workspaces/projects/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": "Local_Ai_Work",
                                  "query": "프로젝트 타입 탐지",
                                  "topK": 5,
                                  "threshold": 0.50
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.instructionEnabled").value(true))
                .andExpect(jsonPath("$.resultCount").value(1))
                .andExpect(jsonPath("$.results[0].rank").value(1))
                .andExpect(jsonPath("$.results[0].similarity").value(0.81))
                .andExpect(jsonPath("$.results[0].filePath")
                        .value("src/main/java/ProjectDiscoveryService.java"))
                .andExpect(jsonPath("$.queryVector").doesNotExist())
                .andExpect(jsonPath("$.results[0].vector").doesNotExist());

        verify(searchService).search(request);
    }
}
