package com.localai.workspace.embedding;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/embeddings")
public class EmbeddingController {

    private final EmbeddingService embeddingService;
    private final String model;

    public EmbeddingController(
            EmbeddingService embeddingService,
            @Value("${spring.ai.ollama.embedding.model}") String model) {
        this.embeddingService = embeddingService;
        this.model = model;
    }

    @PostMapping
    public EmbeddingResponse embed(@Valid @RequestBody EmbeddingRequest request) {
        EmbeddingService.EmbeddingResult result = embeddingService.embed(request.text());
        return new EmbeddingResponse(model, result.dimensions(), result.preview());
    }

    public record EmbeddingRequest(@NotBlank String text) {
    }

    public record EmbeddingResponse(String model, int dimensions, List<Float> preview) {
    }
}
