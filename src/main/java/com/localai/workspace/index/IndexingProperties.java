package com.localai.workspace.index;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "localrag.indexing")
public record IndexingProperties(int expectedDimensions) {

    public IndexingProperties {
        if (expectedDimensions < 1) {
            throw new IllegalArgumentException("Expected embedding dimensions must be positive");
        }
    }
}
