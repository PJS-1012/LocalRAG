package com.localai.workspace.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "localrag.rag.context")
public record RagContextProperties(int maxCharacters) {

    public RagContextProperties {
        if (maxCharacters < 1) {
            throw new IllegalArgumentException("RAG Context max characters must be positive");
        }
    }
}
