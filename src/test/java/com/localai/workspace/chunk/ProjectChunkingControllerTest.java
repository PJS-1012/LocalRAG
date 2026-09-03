package com.localai.workspace.chunk;

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

@WebMvcTest(ProjectChunkingController.class)
class ProjectChunkingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectChunkingService chunkingService;

    @Test
    void previewsChunksForRelativeProjectId() throws Exception {
        String projectId = "Room_Reservation/RoomReservation";
        when(chunkingService.chunk(projectId)).thenReturn(new ProjectChunkingResult(
                "RoomReservation",
                projectId,
                2,
                2,
                0,
                0,
                0,
                3,
                4,
                1500.0,
                900,
                2000,
                12,
                List.of(),
                List.of()
        ));

        mockMvc.perform(post("/api/workspaces/projects/chunks/preview")
                        .param("projectId", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.documentCount").value(2))
                .andExpect(jsonPath("$.chunkCount").value(4))
                .andExpect(jsonPath("$.averageChunkSize").value(1500.0));

        verify(chunkingService).chunk(projectId);
    }
}
