package com.localai.workspace.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
public class EmbeddingService {

    private static final int PREVIEW_SIZE = 8;

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public EmbeddingResult embed(String text) {
        float[] vector = embedVector(text);
        return new EmbeddingResult(vector.length, preview(vector));
    }

    public float[] embedVector(String text) {
        return embeddingModel.embed(text);
    }

    public List<float[]> embedAll(List<String> texts) {
        return embeddingModel.embed(List.copyOf(texts));
    }

    public List<Float> preview(float[] vector) {
        return IntStream.range(0, Math.min(PREVIEW_SIZE, vector.length))
                .mapToObj(index -> vector[index])
                .toList();
    }

    public record EmbeddingResult(int dimensions, List<Float> preview) {
    }
}
