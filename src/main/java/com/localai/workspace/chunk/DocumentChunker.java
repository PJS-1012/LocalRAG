package com.localai.workspace.chunk;

import com.localai.workspace.document.WorkspaceDocument;

import java.util.List;

public interface DocumentChunker {

    boolean supports(WorkspaceDocument document);

    List<ChunkSlice> split(WorkspaceDocument document);
}
