package com.localai.workspace.chunk;

import com.localai.workspace.document.WorkspaceDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkingServiceTest {

    private final ChunkingProperties properties = new ChunkingProperties(20, 24, 4);
    private final DocumentChunkingService service = new DocumentChunkingService(
            new TextDocumentChunker(properties),
            new SourceCodeDocumentChunker(properties)
    );

    @Test
    void returnsNoChunksForEmptyDocument() {
        assertThat(service.chunk(document("md", ""))).isEmpty();
        assertThat(service.chunk(document("txt", "   \n"))).isEmpty();
    }

    @Test
    void keepsShortAndExactBoundaryDocumentsInOneChunk() {
        List<DocumentChunk> shortChunks = service.chunk(document("md", "short text"));
        List<DocumentChunk> exactChunks = service.chunk(document("txt", "x".repeat(20)));

        assertThat(shortChunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.content()).isEqualTo("short text");
            assertThat(chunk.startOffset()).isZero();
            assertThat(chunk.endOffset()).isEqualTo(10);
            assertThat(chunk.startLine()).isEqualTo(1);
            assertThat(chunk.endLine()).isEqualTo(1);
        });
        assertThat(exactChunks).singleElement()
                .extracting(chunk -> chunk.content().length())
                .isEqualTo(20);
    }

    @Test
    void splitsLongParagraphWithConfiguredOverlapAndBounds() {
        List<DocumentChunk> chunks = service.chunk(document("md", "x".repeat(55)));

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.content().length()).isLessThanOrEqualTo(20));
        assertThat(chunks.get(0).endOffset() - chunks.get(1).startOffset()).isEqualTo(4);
    }

    @Test
    void usesLargerSourceCodeLimitAndTracksLines() {
        String source = "class Sample {\n" + "x".repeat(45) + "\n}\n";

        List<DocumentChunk> chunks = service.chunk(document("java", source));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.content().length()).isLessThanOrEqualTo(24));
        assertThat(chunks).anyMatch(chunk -> chunk.content().length() > 20);
        assertThat(chunks.get(chunks.size() - 1).endLine()).isEqualTo(3);
    }

    @Test
    void keepsMarkdownHeadingAsAChunkBoundaryWhenPossible() {
        String content = "intro ".repeat(2) + "\n\n# Next\n" + "detail ".repeat(3);

        List<DocumentChunk> chunks = service.chunk(document("md", content));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(1).content()).contains("# Next");
    }

    @Test
    void prefersAnIndentedClosingBraceAsSourceBoundary() {
        String source = "void first() {\n"
                + "    runSomething();\n"
                + "    runSomethingElse();\n"
                + "}\n"
                + "void second() {\n"
                + "    continueWork();\n"
                + "}\n";
        ChunkingProperties blockProperties = new ChunkingProperties(50, 70, 4);
        DocumentChunkingService blockService = new DocumentChunkingService(
                new TextDocumentChunker(blockProperties),
                new SourceCodeDocumentChunker(blockProperties)
        );

        List<DocumentChunk> chunks = blockService.chunk(document("java", source));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).content()).endsWith("}\n");
    }
    @Test
    void createsStableUniqueIdsForRepeatedContent() {
        WorkspaceDocument document = document("txt", "repeat ".repeat(12));

        List<String> firstIds = service.chunk(document).stream().map(DocumentChunk::chunkId).toList();
        List<String> secondIds = service.chunk(document).stream().map(DocumentChunk::chunkId).toList();

        assertThat(secondIds).containsExactlyElementsOf(firstIds);
        assertThat(firstIds).doesNotHaveDuplicates();
        assertThat(firstIds).allSatisfy(id -> assertThat(id).hasSize(64));
    }

    private WorkspaceDocument document(String extension, String content) {
        return new WorkspaceDocument(
                "Local_Ai_Work",
                "Local_Ai_Work",
                "sample." + extension,
                "C:/workspace/Local_Ai_Work/sample." + extension,
                "sample." + extension,
                extension,
                content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                Instant.parse("2026-09-04T00:00:00Z"),
                content
        );
    }
}
