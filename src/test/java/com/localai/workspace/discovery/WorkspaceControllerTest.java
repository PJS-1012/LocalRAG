package com.localai.workspace.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkspaceController.class)
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectDiscoveryService discoveryService;

    @Test
    void returnsProjectsAndContainers() throws Exception {
        Path workspace = Path.of("C:/workspace");
        DetectedProject nested = new DetectedProject(
                "nested", "container/nested", workspace.resolve("container/nested"), ProjectType.NODE,
                "NODE", false, true, List.of("package.json")
        );
        when(discoveryService.discoverWorkspace()).thenReturn(new WorkspaceDiscoveryResult(
                workspace,
                List.of(nested),
                List.of(new DetectedContainer(
                        "container", workspace.resolve("container"), true, List.of(nested)
                ))
        ));

        mockMvc.perform(get("/api/workspaces/discovery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects[0].name").value("nested"))
                .andExpect(jsonPath("$.containers[0].name").value("container"))
                .andExpect(jsonPath("$.containers[0].projects[0].projectType").value("NODE"));

        verify(discoveryService).discoverWorkspace();
    }
}
