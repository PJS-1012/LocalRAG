package com.localai.workspace.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;

@Service
public class OllamaReadOnlyService {

    private final URI tagsUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LocalEnvironmentProperties properties;

    public OllamaReadOnlyService(
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            ObjectMapper objectMapper,
            LocalEnvironmentProperties properties
    ) {
        this.tagsUri = toTagsUri(baseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.httpTimeout())
                .build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public OllamaStatusResult getStatus() {
        if (tagsUri == null) {
            return failure(LocalEnvironmentStatus.NOT_CONFIGURED, "Ollama base URL is not configured correctly");
        }

        HttpRequest request = HttpRequest.newBuilder(tagsUri)
                .timeout(properties.httpTimeout())
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return failure(LocalEnvironmentStatus.ACCESS_DENIED, "Ollama access was denied");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return failure(LocalEnvironmentStatus.UNAVAILABLE, "Ollama returned an unavailable status");
            }
            if (response.body().length() > properties.maxOutputCharacters()) {
                return failure(LocalEnvironmentStatus.FAILED, "Ollama model list exceeded the safe output limit");
            }

            JsonNode modelsNode = objectMapper.readTree(response.body()).path("models");
            List<String> models = new ArrayList<>();
            if (modelsNode.isArray()) {
                for (JsonNode modelNode : modelsNode) {
                    if (models.size() >= properties.maxModels()) {
                        break;
                    }
                    String name = modelNode.path("name").asText("");
                    if (!name.isBlank()) {
                        models.add(name);
                    }
                }
            }
            return new OllamaStatusResult(
                    LocalEnvironmentStatus.AVAILABLE, true, models.size(), List.copyOf(models), null
            );
        } catch (HttpTimeoutException exception) {
            return failure(LocalEnvironmentStatus.UNAVAILABLE, "Ollama status request timed out");
        } catch (ConnectException exception) {
            return failure(LocalEnvironmentStatus.NOT_RUNNING, "Ollama is not currently reachable");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failure(LocalEnvironmentStatus.FAILED, "Ollama status request was interrupted");
        } catch (IOException | RuntimeException exception) {
            return failure(LocalEnvironmentStatus.FAILED, "Ollama status could not be read");
        }
    }

    private URI toTagsUri(String baseUrl) {
        try {
            String normalized = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            return URI.create(normalized).resolve("api/tags");
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private OllamaStatusResult failure(LocalEnvironmentStatus status, String reason) {
        return new OllamaStatusResult(status, false, 0, List.of(), reason);
    }
}
