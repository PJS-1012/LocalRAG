package com.localai.workspace.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaReadOnlyServiceTest {

    private final LocalEnvironmentProperties properties = new LocalEnvironmentProperties(
            Duration.ofSeconds(1), Duration.ofSeconds(1), 65_536, 100
    );

    @Test
    void readsAvailableModelsFromConfiguredOllamaEndpoint() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            byte[] body = """
                    {"models":[{"name":"qwen3:8b"},{"name":"qwen3-embedding:0.6b"}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            OllamaReadOnlyService service = new OllamaReadOnlyService(
                    "http://127.0.0.1:" + server.getAddress().getPort(), new ObjectMapper(), properties
            );

            OllamaStatusResult result = service.getStatus();

            assertThat(result.status()).isEqualTo(LocalEnvironmentStatus.AVAILABLE);
            assertThat(result.reachable()).isTrue();
            assertThat(result.models()).containsExactly("qwen3:8b", "qwen3-embedding:0.6b");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void isolatesOllamaUnavailableWithoutStoppingTheRealAgentModel() throws Exception {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        OllamaReadOnlyService service = new OllamaReadOnlyService(
                "http://127.0.0.1:" + unusedPort, new ObjectMapper(), properties
        );

        OllamaStatusResult result = service.getStatus();

        assertThat(result.status()).isIn(LocalEnvironmentStatus.NOT_RUNNING, LocalEnvironmentStatus.FAILED);
        assertThat(result.reachable()).isFalse();
        assertThat(result.models()).isEmpty();
    }
}
