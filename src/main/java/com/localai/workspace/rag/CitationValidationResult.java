package com.localai.workspace.rag;

import java.util.List;

public record CitationValidationResult(
        List<String> usedSourceIds,
        List<String> invalidSourceIds,
        boolean citationMissing
) {
}
