package com.localai.workspace.chunk;

import com.localai.workspace.document.WorkspaceDocument;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class SourceCodeDocumentChunker implements DocumentChunker {

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            "java", "kt", "kts", "cs", "js", "jsx", "ts", "tsx", "py",
            "c", "cpp", "h", "hpp", "sql", "gradle", "ps1", "sh", "html", "css"
    );

    private final ChunkingProperties properties;

    public SourceCodeDocumentChunker(ChunkingProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supports(WorkspaceDocument document) {
        String extension = document.extension() == null
                ? ""
                : document.extension().toLowerCase(Locale.ROOT);
        return SOURCE_EXTENSIONS.contains(extension);
    }

    @Override
    public List<ChunkSlice> split(WorkspaceDocument document) {
        return CharacterChunkSplitter.split(
                document.content(),
                properties.sourceMaxCharacters(),
                properties.overlapCharacters(),
                CharacterChunkSplitter.BoundaryStyle.SOURCE
        );
    }
}
