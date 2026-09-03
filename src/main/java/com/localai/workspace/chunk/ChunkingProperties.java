package com.localai.workspace.chunk;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "localrag.chunking")
public record ChunkingProperties(
        int textMaxCharacters,
        int sourceMaxCharacters,
        int overlapCharacters
) {
    public ChunkingProperties {
        if (textMaxCharacters < 1 || sourceMaxCharacters < 1) {
            throw new IllegalArgumentException("Chunk maximum size must be positive");
        }
        if (overlapCharacters < 0
                || overlapCharacters >= textMaxCharacters
                || overlapCharacters >= sourceMaxCharacters) {
            throw new IllegalArgumentException("Chunk overlap must be non-negative and smaller than each maximum");
        }
    }
}
