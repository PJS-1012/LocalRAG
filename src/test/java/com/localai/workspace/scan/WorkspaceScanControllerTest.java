package com.localai.workspace.scan;

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

@WebMvcTest(WorkspaceScanController.class)
class WorkspaceScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkspaceMetadataScanService metadataScanService;

    @Test
    void returnsWorkspaceMetadataSummary() throws Exception {
        when(metadataScanService.scan()).thenReturn(new WorkspaceScanSummary(
                "C:\\workspace", 10, 9, 1, 100, 20, 78, 2, 0, 50, List.of()
        ));

        mockMvc.perform(post("/api/workspaces/scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProjects").value(10))
                .andExpect(jsonPath("$.failedProjects").value(1))
                .andExpect(jsonPath("$.totalFiles").value(100));

        verify(metadataScanService).scan();
    }
}
