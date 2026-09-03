package com.localai.workspace.document;

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

@WebMvcTest(ProjectDocumentBatchController.class)
class ProjectDocumentBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectDocumentBatchReader batchReader;

    @Test
    void returnsProjectReadSummary() throws Exception {
        when(batchReader.read("Local_Ai_Work")).thenReturn(new ProjectDocumentReadResult(
                "Local_Ai_Work", "Local_Ai_Work", 10, 4, 1, 5, 2048, 12, List.of(), List.of()
        ));

        mockMvc.perform(post("/api/workspaces/projects/Local_Ai_Work/documents/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectName").value("Local_Ai_Work"))
                .andExpect(jsonPath("$.successCount").value(4))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.totalTextBytes").value(2048));

        verify(batchReader).read("Local_Ai_Work");
    }

    @Test
    void readsAllFromNestedProjectByRelativeProjectId() throws Exception {
        String projectId = "Toy_Sports_Day/toy_sports_day";
        when(batchReader.read(projectId)).thenReturn(new ProjectDocumentReadResult(
                "toy_sports_day", projectId, 10, 4, 0, 6, 2048, 12, List.of(), List.of()
        ));

        mockMvc.perform(post("/api/workspaces/projects/documents/read-all")
                        .param("projectId", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.projectName").value("toy_sports_day"));

        verify(batchReader).read(projectId);
    }
}
