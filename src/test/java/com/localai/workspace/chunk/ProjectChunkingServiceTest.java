package com.localai.workspace.chunk;

import com.localai.workspace.document.ProjectDocumentBatchReader;
import com.localai.workspace.document.ProjectDocumentReadResult;
import com.localai.workspace.document.WorkspaceDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectChunkingServiceTest {

    @Test
    void isolatesOneDocumentFailureAndKeepsProjectStatistics() {
        ProjectDocumentBatchReader reader = mock(ProjectDocumentBatchReader.class);
        DocumentChunkingService chunker = mock(DocumentChunkingService.class);
        WorkspaceDocument successful = document("README.md", "# README");
        WorkspaceDocument failed = document("broken.md", "broken");

        when(reader.read("Local_Ai_Work")).thenReturn(new ProjectDocumentReadResult(
                "Local_Ai_Work",
                "Local_Ai_Work",
                5,
                2,
                1,
                2,
                15,
                3,
                List.of(successful, failed),
                List.of()
        ));
        when(chunker.chunk(successful)).thenReturn(List.of(chunk(successful)));
        when(chunker.chunk(failed)).thenThrow(new IllegalStateException("synthetic failure"));

        ProjectChunkingResult result = new ProjectChunkingService(reader, chunker)
                .chunk("Local_Ai_Work");

        assertThat(result.documentCount()).isEqualTo(2);
        assertThat(result.chunkedDocumentCount()).isEqualTo(1);
        assertThat(result.failedDocumentCount()).isEqualTo(1);
        assertThat(result.sourceReadFailedCount()).isEqualTo(1);
        assertThat(result.sourceSkippedCount()).isEqualTo(2);
        assertThat(result.chunkCount()).isEqualTo(1);
        assertThat(result.minChunkSize()).isEqualTo(8);
        assertThat(result.maxChunkSize()).isEqualTo(8);
        assertThat(result.documentResults())
                .filteredOn(file -> file.status() == ChunkingStatus.CHUNK_FAILED)
                .singleElement()
                .satisfies(file -> {
                    assertThat(file.sourceFilePath()).isEqualTo("broken.md");
                    assertThat(file.reason()).contains("synthetic failure");
                });
    }

    private WorkspaceDocument document(String path, String content) {
        return new WorkspaceDocument(
                "Local_Ai_Work",
                "Local_Ai_Work",
                path,
                "C:/workspace/Local_Ai_Work/" + path,
                path,
                "md",
                content.length(),
                Instant.parse("2026-09-04T00:00:00Z"),
                content
        );
    }

    private DocumentChunk chunk(WorkspaceDocument document) {
        return new DocumentChunk(
                "a".repeat(64),
                document.projectId(),
                document.relativePath(),
                document.fileName(),
                document.extension(),
                0,
                document.content(),
                0,
                document.content().length(),
                1,
                1,
                "b".repeat(64),
                document.modifiedAt()
        );
    }
}
