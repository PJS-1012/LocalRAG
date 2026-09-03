package com.localai.workspace.chunk;

import com.localai.workspace.document.ProjectDocumentBatchReader;
import com.localai.workspace.document.ProjectDocumentReadResult;
import com.localai.workspace.document.WorkspaceDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.IntSummaryStatistics;
import java.util.List;

@Service
public class ProjectChunkingService {

    private final ProjectDocumentBatchReader documentReader;
    private final DocumentChunkingService chunkingService;

    public ProjectChunkingService(
            ProjectDocumentBatchReader documentReader,
            DocumentChunkingService chunkingService
    ) {
        this.documentReader = documentReader;
        this.chunkingService = chunkingService;
    }

    public ProjectChunkingResult chunk(String projectId) {
        long startedAt = System.nanoTime();
        ProjectDocumentReadResult readResult = documentReader.read(projectId);
        List<DocumentChunk> chunks = new ArrayList<>();
        List<DocumentChunkingResult> documentResults = new ArrayList<>();

        for (WorkspaceDocument document : readResult.documents()) {
            try {
                List<DocumentChunk> documentChunks = chunkingService.chunk(document);
                chunks.addAll(documentChunks);
                documentResults.add(new DocumentChunkingResult(
                        document.relativePath(),
                        documentChunks.isEmpty() ? ChunkingStatus.EMPTY : ChunkingStatus.CHUNKED,
                        documentChunks.size(),
                        null
                ));
            } catch (RuntimeException exception) {
                documentResults.add(new DocumentChunkingResult(
                        document.relativePath(),
                        ChunkingStatus.CHUNK_FAILED,
                        0,
                        exception.getClass().getSimpleName() + ": " + exception.getMessage()
                ));
            }
        }

        IntSummaryStatistics sizeStatistics = chunks.stream()
                .mapToInt(chunk -> chunk.content().length())
                .summaryStatistics();
        long chunkedDocuments = documentResults.stream()
                .filter(result -> result.status() == ChunkingStatus.CHUNKED)
                .count();
        long emptyDocuments = documentResults.stream()
                .filter(result -> result.status() == ChunkingStatus.EMPTY)
                .count();
        long failedDocuments = documentResults.stream()
                .filter(result -> result.status() == ChunkingStatus.CHUNK_FAILED)
                .count();

        return new ProjectChunkingResult(
                readResult.projectName(),
                readResult.projectId(),
                readResult.documents().size(),
                chunkedDocuments,
                emptyDocuments,
                failedDocuments,
                readResult.failedCount(),
                readResult.skippedCount(),
                chunks.size(),
                sizeStatistics.getCount() == 0 ? 0.0 : sizeStatistics.getAverage(),
                sizeStatistics.getCount() == 0 ? 0 : sizeStatistics.getMin(),
                sizeStatistics.getCount() == 0 ? 0 : sizeStatistics.getMax(),
                (System.nanoTime() - startedAt) / 1_000_000,
                List.copyOf(chunks),
                List.copyOf(documentResults)
        );
    }
}
