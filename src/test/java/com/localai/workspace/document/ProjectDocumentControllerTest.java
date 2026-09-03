package com.localai.workspace.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectDocumentController.class)
class ProjectDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectDocumentReader documentReader;

    @Test
    void readsExactlyOneRequestedFile() throws Exception {
        WorkspaceDocument document = new WorkspaceDocument(
                "Local_Ai_Work",
                "Local_Ai_Work",
                "README.md",
                "C:\\workspace\\Local_Ai_Work\\README.md",
                "README.md",
                "md",
                42,
                Instant.parse("2026-09-03T00:00:00Z"),
                "# LocalRAG"
        );
        when(documentReader.read("Local_Ai_Work", "README.md"))
                .thenReturn(DocumentReadResult.success(document));

        mockMvc.perform(post("/api/workspaces/projects/Local_Ai_Work/documents/read")
                        .param("filePath", "README.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READ_SUCCESS"))
                .andExpect(jsonPath("$.document.projectName").value("Local_Ai_Work"))
                .andExpect(jsonPath("$.document.fileName").value("README.md"))
                .andExpect(jsonPath("$.document.content").value("# LocalRAG"));

        verify(documentReader).read("Local_Ai_Work", "README.md");
    }

    @Test
    void readsNestedProjectByRelativeProjectId() throws Exception {
        String projectId = "Room_Reservation/RoomReservation";
        WorkspaceDocument document = new WorkspaceDocument(
                "RoomReservation",
                projectId,
                "README.md",
                "C:\\workspace\\Room_Reservation\\RoomReservation\\README.md",
                "README.md",
                "md",
                42,
                Instant.parse("2026-09-03T00:00:00Z"),
                "# Room Reservation"
        );
        when(documentReader.read(projectId, "README.md"))
                .thenReturn(DocumentReadResult.success(document));

        mockMvc.perform(post("/api/workspaces/projects/documents/read")
                        .param("projectId", projectId)
                        .param("filePath", "README.md"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.projectId").value(projectId))
                .andExpect(jsonPath("$.document.projectName").value("RoomReservation"));

        verify(documentReader).read(projectId, "README.md");
    }
}
