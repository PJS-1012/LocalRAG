package com.localai.workspace.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "localrag.search")
public record ProjectSearchProperties(
        int defaultTopK,
        double defaultThreshold,
        int maxTopK
) {
    public ProjectSearchProperties {
        if (defaultTopK < 1 || maxTopK < defaultTopK) {
            throw new IllegalArgumentException("Search Top-K configuration is invalid");
        }
        if (defaultThreshold < 0.0 || defaultThreshold > 1.0) {
            throw new IllegalArgumentException("Search threshold must be between 0 and 1");
        }
    }
}
