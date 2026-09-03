package com.localai.workspace.chunk;

import com.localai.workspace.document.WorkspaceDocument;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

@Service
public class DocumentChunkingService {

    private final TextDocumentChunker textChunker;
    private final SourceCodeDocumentChunker sourceCodeChunker;

    public DocumentChunkingService(
            TextDocumentChunker textChunker,
            SourceCodeDocumentChunker sourceCodeChunker
    ) {
        this.textChunker = textChunker;
        this.sourceCodeChunker = sourceCodeChunker;
    }

    public List<DocumentChunk> chunk(WorkspaceDocument document) {
        Objects.requireNonNull(document, "document");
        String content = document.content();
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String fingerprint = sha256(content);
        DocumentChunker chunker = sourceCodeChunker.supports(document)
                ? sourceCodeChunker
                : textChunker;
        List<ChunkSlice> slices = chunker.split(document);

        return IntStream.range(0, slices.size())
                .mapToObj(index -> toChunk(document, slices.get(index), fingerprint, index))
                .toList();
    }

    private DocumentChunk toChunk(
            WorkspaceDocument document,
            ChunkSlice slice,
            String fingerprint,
            int index
    ) {
        String chunkId = sha256(String.join(
                "\n",
                document.projectId(),
                document.relativePath(),
                fingerprint,
                Integer.toString(index)
        ));
        return new DocumentChunk(
                chunkId,
                document.projectId(),
                document.relativePath(),
                document.fileName(),
                document.extension(),
                index,
                slice.content(),
                slice.startOffset(),
                slice.endOffset(),
                slice.startLine(),
                slice.endLine(),
                fingerprint,
                document.modifiedAt()
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
