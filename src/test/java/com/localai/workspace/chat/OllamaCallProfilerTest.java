package com.localai.workspace.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class OllamaCallProfilerTest {
    private final OllamaCallProfiler profiler=new OllamaCallProfiler(new ObjectMapper());
    private final MockClientHttpRequest request=new MockClientHttpRequest(HttpMethod.POST,URI.create("http://localhost:11434/api/chat"));
    private byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private final String input="""
        {"model":"qwen3:8b","stream":false,"messages":[{"role":"user","content":"PRIVATE_QUERY"}],
         "tools":[{"type":"function","function":{"name":"getGitStatus"}}]}
        """;
    @Test void countsActualRoundsAndPreservesResponseBodyWithoutLeakingContent() throws Exception {
        try(var trace=UnifiedRequestTrace.begin()) {
            String selection="""
                {"message":{"content":"","tool_calls":[{"function":{"name":"getGitStatus","arguments":{}}}]},
                 "prompt_eval_count":21,"eval_count":4,"eval_duration":12000000,"done":true,"done_reason":"stop"}
                """;
            var first=profiler.interceptor().intercept(request,bytes(input),
                    (r,b)->new MockClientHttpResponse(bytes(selection),HttpStatus.OK));
            assertThat(new String(first.getBody().readAllBytes(),StandardCharsets.UTF_8)).isEqualTo(selection);
            String answer="""
                {"message":{"content":"PRIVATE_ANSWER"},"prompt_eval_count":35,"eval_count":9,"done":true,"done_reason":"stop"}
                """;
            profiler.interceptor().intercept(request,bytes(input),
                    (r,b)->new MockClientHttpResponse(bytes(answer),HttpStatus.OK));
            var snapshot=trace.snapshot();
            assertThat(snapshot.llmCallCount()).isEqualTo(2);
            assertThat(snapshot.llmCalls()).extracting(UnifiedRequestTrace.ModelCall::purpose)
                    .containsExactly("TOOL_SELECTION","FINAL_ANSWER");
            assertThat(snapshot.llmCalls().get(0).inputContextChars()).isEqualTo(13);
            assertThat(snapshot.llmCalls().get(0).toolSchemaChars()).isPositive();
            assertThat(snapshot.llmCalls().get(0).generationMs()).isEqualTo(12);
            assertThat(snapshot.toString()).doesNotContain("PRIVATE_QUERY","PRIVATE_ANSWER");
        }
        assertThat(UnifiedRequestTrace.current()).isNull();
    }
    @Test void http200DoesNotMeanGenerationCompleted() throws Exception {
        for(String completion:java.util.List.of("",",\"done\":false,\"done_reason\":\"stop\"",
                ",\"done\":true,\"done_reason\":\"length\""))try(var trace=UnifiedRequestTrace.begin()) {
            profiler.interceptor().intercept(request,bytes(input),(r,b)->new MockClientHttpResponse(
                    bytes("{\"message\":{\"content\":\"partial\"}"+completion+"}"),HttpStatus.OK));
            assertThat(trace.snapshot().llmCalls().get(0).success()).isFalse();
        }
    }
    @Test void capturesFailedAttemptsAndDoesNotSwallowTransportFailure() {
        try(var trace=UnifiedRequestTrace.begin()) {
            ClientHttpRequestExecution failed=(r,b)->{throw new IOException("secret transport detail");};
            assertThatThrownBy(()->profiler.interceptor().intercept(request,bytes(input),failed)).isInstanceOf(IOException.class);
            assertThat(trace.snapshot().llmCallCount()).isEqualTo(1);
            assertThat(trace.snapshot().llmCalls().get(0).success()).isFalse();
            assertThat(trace.snapshot().toString()).doesNotContain("secret transport detail");
        }
    }
    @Test void failedProviderResponseRemainsReadableAndIsCounted() throws Exception {
        try(var trace=UnifiedRequestTrace.begin()) {
            var response=profiler.interceptor().intercept(request,bytes(input),
                    (r,b)->new MockClientHttpResponse(bytes("{\"error\":\"unavailable\"}"),HttpStatus.SERVICE_UNAVAILABLE));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(trace.snapshot().llmCalls().get(0).success()).isFalse();
        }
    }
    @Test void measuresNestedStageSubtotalsWithoutPretendingTheyAreSeparateModelCalls() {
        try(var trace=UnifiedRequestTrace.begin()) {
            UnifiedRequestTrace.recordStage("TOOL_EXECUTION",30);
            UnifiedRequestTrace.recordStage("PROGRESS_ACTIVITY",30);
            UnifiedRequestTrace.recordStage("VECTOR_SEARCH",2);
            assertThat(trace.snapshot().llmCallCount()).isZero();
            assertThat(trace.snapshot().evidenceCollectionMs()).isEqualTo(30);
            assertThat(trace.snapshot().vectorSearchMs()).isEqualTo(2);
            assertThat(trace.snapshot().queryDecompositionMs()).isZero();
        }
    }
}
