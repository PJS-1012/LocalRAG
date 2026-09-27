package com.localai.workspace.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.io.*;

/** Observes the HTTP boundary so Spring AI's internal Tool loop cannot hide model calls. */
@Component
public class OllamaCallProfiler implements RestClientCustomizer {
    private final ObjectMapper json;
    public OllamaCallProfiler(ObjectMapper json) { this.json = json; }
    @Override public void customize(RestClient.Builder builder) { builder.requestInterceptor(interceptor()); }

    ClientHttpRequestInterceptor interceptor() {
        return (request, body, execution) -> {
            var trace = UnifiedRequestTrace.current();
            if (trace == null || !request.getURI().getPath().endsWith("/api/chat"))
                return execution.execute(request, body);
            JsonNode input;
            try { input = json.readTree(body); }
            catch (IOException ignored) { return execution.execute(request, body); }
            if (input == null || input.path("stream").asBoolean(false))
                return execution.execute(request, body);
            long inputChars = 0;
            for (var message : input.path("messages")) {
                inputChars += message.path("content").asText("").length();
                if (message.has("tool_calls")) inputChars += message.get("tool_calls").toString().length();
            }
            long schemaChars = input.has("tools") && !input.path("tools").isEmpty() ? input.get("tools").toString().length() : 0;
            long start = System.nanoTime();
            try {
                ClientHttpResponse response = execution.execute(request, body);
                byte[] bytes;
                try { bytes = response.getBody().readAllBytes(); }
                catch (IOException error) { response.close(); throw error; }
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                JsonNode result;
                try { result = json.readTree(bytes); }
                catch (IOException ignored) { result = json.createObjectNode(); }
                if (result == null) result = json.createObjectNode();
                var message = result.path("message");
                boolean toolSelection = message.path("tool_calls").isArray() && !message.path("tool_calls").isEmpty();
                String finish=result.path("done_reason").asText("unknown");
                boolean success = response.getStatusCode().is2xxSuccessful() && !result.has("error")
                        && result.path("done").asBoolean(false)
                        && (finish.equals("stop") || toolSelection && finish.equals("tool_calls"));
                long outputChars = message.path("content").asText("").length();
                if (toolSelection) outputChars += message.get("tool_calls").toString().length();
                trace.addCall(success ? (toolSelection ? "TOOL_SELECTION" : "FINAL_ANSWER") : "FAILED_REQUEST",
                        input.path("model").asText("unknown"), inputChars, schemaChars, outputChars,
                        message.path("thinking").asText("").length(), elapsed, success,
                        result.path("prompt_eval_count").asLong(), result.path("eval_count").asLong(),
                        millis(result, "load_duration"), millis(result, "prompt_eval_duration"),
                        millis(result, "eval_duration"), finish);
                return replay(response, bytes);
            } catch (IOException | RuntimeException error) {
                trace.addCall("FAILED_REQUEST", input.path("model").asText("unknown"), inputChars,
                        schemaChars, 0, 0, (System.nanoTime() - start) / 1_000_000, false,
                        0, 0, 0, 0, 0, "transport_error");
                throw error;
            }
        };
    }
    private long millis(JsonNode node, String field) { return node.path(field).asLong() / 1_000_000; }
    private ClientHttpResponse replay(ClientHttpResponse response, byte[] bytes) {
        return new ClientHttpResponse() {
            @Override public HttpStatusCode getStatusCode() throws IOException { return response.getStatusCode(); }
            @Override public String getStatusText() throws IOException { return response.getStatusText(); }
            @Override public HttpHeaders getHeaders() { return response.getHeaders(); }
            @Override public InputStream getBody() { return new ByteArrayInputStream(bytes); }
            @Override public void close() { response.close(); }
        };
    }
}
