package com.localai.workspace.errors;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "localrag.errors.similarity")
public record ErrorSimilarityProperties(
        int defaultTopK, int maxTopK, double threshold, String embeddingModel, int expectedDimensions
) {
    public ErrorSimilarityProperties {
        if (defaultTopK < 1) defaultTopK = 5;
        if (maxTopK < defaultTopK) maxTopK = 20;
        if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1) threshold = 0.65;
        if (embeddingModel == null || embeddingModel.isBlank()) embeddingModel = "qwen3-embedding:0.6b";
        if (expectedDimensions < 1) expectedDimensions = 1024;
    }
}
