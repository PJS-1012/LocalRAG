package com.localai.workspace.scan;

import com.localai.workspace.discovery.ProjectType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectScanController.class)
class ProjectScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectFileScanner projectFileScanner;

    @Test
    void scansNestedProjectByRelativeProjectId() throws Exception {
        String projectId = "Room_Reservation/RoomReservation";
        when(projectFileScanner.scan(projectId)).thenReturn(new ProjectScanResult(
                "RoomReservation",
                projectId,
                "C:\\workspace\\Room_Reservation\\RoomReservation",
                ProjectType.JAVA,
                10,
                4,
                6,
                0,
                0,
                Map.of(),
                List.of(),
                List.of()
        ));

        mockMvc.perform(post("/api/workspaces/projects/scan")
                        .param("projectId", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.project").value("RoomReservation"));

        verify(projectFileScanner).scan(projectId);
    }
}
