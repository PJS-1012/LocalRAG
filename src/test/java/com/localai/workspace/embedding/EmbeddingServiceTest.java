package com.localai.workspace.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingServiceTest {

    @Test
    void returnsDimensionsAndLimitedPreview() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        float[] vector = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f};
        when(embeddingModel.embed("예약 생성 로직")).thenReturn(vector);

        EmbeddingService service = new EmbeddingService(embeddingModel);
        EmbeddingService.EmbeddingResult result = service.embed("예약 생성 로직");

        assertThat(result.dimensions()).isEqualTo(9);
        assertThat(result.preview()).containsExactly(
                0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f);
        verify(embeddingModel).embed("예약 생성 로직");
    }

    @Test
    void delegatesBatchEmbeddingToTheExistingModel() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        List<String> texts = List.of("first", "second");
        List<float[]> vectors = List.of(new float[4], new float[4]);
        when(embeddingModel.embed(texts)).thenReturn(vectors);

        EmbeddingService service = new EmbeddingService(embeddingModel);

        assertThat(service.embedAll(texts)).isSameAs(vectors);
        verify(embeddingModel).embed(texts);
    }
}
