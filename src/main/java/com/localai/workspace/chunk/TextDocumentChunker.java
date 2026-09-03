package com.localai.workspace.chunk;

import com.localai.workspace.document.WorkspaceDocument;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TextDocumentChunker implements DocumentChunker {

    private final ChunkingProperties properties;

    public TextDocumentChunker(ChunkingProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(WorkspaceDocument document) {
        return true;
    }

    @Override
    public List<ChunkSlice> split(WorkspaceDocument document) {
        return CharacterChunkSplitter.split(
                document.content(),
                properties.textMaxCharacters(),
                properties.overlapCharacters(),
                CharacterChunkSplitter.BoundaryStyle.TEXT
        );
    }
}
