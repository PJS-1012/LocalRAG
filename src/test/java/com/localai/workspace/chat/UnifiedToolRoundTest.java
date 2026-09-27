package com.localai.workspace.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UnifiedToolRoundTest {
    ToolCallback callback(String name,String output) {
        var callback=mock(ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(ToolDefinition.builder().name(name).description("read only")
                .inputSchema("{\"type\":\"object\",\"properties\":{}}").build());
        when(callback.call(anyString())).thenReturn(output);
        return callback;
    }
    AssistantMessage.ToolCall call(String name,String args) { return new AssistantMessage.ToolCall("id","function",name,args); }
    ChatResponse response(String text,List<AssistantMessage.ToolCall> calls) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content(text).toolCalls(calls).build())));
    }
    @Test void exactlyTwoModelCallsAndFinalPromptHasNoToolSchemaOrDraft() {
        var model=mock(ChatModel.class);
        var callback=callback("searchProjectKnowledge","{\"status\":\"SUCCESS\",\"sources\":[{\"citationId\":\"K1-S1\",\"content\":\"source body\"}]}");
        when(model.call(any(Prompt.class))).thenReturn(response("UNVERIFIED_DRAFT",List.of(call("searchProjectKnowledge","{}"))),
                response("확인된 코드 [K1-S1]",List.of()));
        var service=new ChatService(ChatClient.builder(model),16384);
        assertThat(service.chatUnifiedWithToolCallbacks("policy","query",callback)).contains("[K1-S1]");
        var prompts=org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model,times(2)).call(prompts.capture());
        var finalPrompt=prompts.getAllValues().get(1);
        assertThat(finalPrompt.getContents()).contains("source body").doesNotContain("UNVERIFIED_DRAFT");
        var options=(OllamaChatOptions)finalPrompt.getOptions();
        assertThat(options.getToolCallbacks()).isEmpty();
        assertThat(options.getInternalToolExecutionEnabled()).isFalse();
        verify(callback,times(1)).call("{}");
    }
    @Test void generalQuestionMakesOnlyOneModelCallAndNoTools() {
        var model=mock(ChatModel.class);
        var callback=callback("getGitStatus","{}");
        when(model.call(any(Prompt.class))).thenReturn(response("[GENERAL] HashMap 설명",List.of()));
        var service=new ChatService(ChatClient.builder(model),16384);
        assertThat(service.chatUnifiedWithToolCallbacks("policy","query",callback)).startsWith("[GENERAL]");
        verify(model,times(1)).call(any(Prompt.class));verify(callback,never()).call(anyString());
    }
    @Test void handoverAddsOnlyExistingProgressAndActivityAndDeduplicatesPlan() {
        var knowledge=callback("searchProjectKnowledge","{\"status\":\"SUCCESS\"}");
        var progress=callback("analyzeProjectProgress","{\"status\":\"SUCCESS\"}");
        var activity=callback("summarizeRecentDevelopment","{\"status\":\"SUCCESS\"}");
        var docker=callback("getDockerStatus","{}");
        try(var trace=UnifiedRequestTrace.begin()) {
            trace.projectId("A/backend");
            var entry=call("searchProjectKnowledge","{\"query\":\"인수인계\",\"intent\":\"HANDOVER\"}");
            UnifiedToolRound.execute(List.of(entry,entry),new ToolCallback[]{knowledge,progress,activity,docker});
            verify(knowledge,times(1)).call(anyString());verify(progress).call("{\"projectId\":\"A/backend\"}");
            verify(activity).call("{\"projectId\":\"A/backend\"}");verify(docker,never()).call(anyString());
            assertThat(trace.snapshot().intent()).isEqualTo("HANDOVER");
        }
    }
    @Test void compactKeepsEvidenceAndUncertaintyButRemovesDuplicatedCompletionNarrative() throws Exception {
        var input=new ObjectMapper().readTree("""
            {"summary":"Duplicated narrative","completed":["Recorded commit 123"],
             "unknown":["Tests not run"],"evidence":[{"id":"GIT-C1","sourceType":"GIT","summary":"Recorded change"},
             {"id":"GIT-C1","sourceType":"GIT","summary":"Recorded change"}]}
            """);
        var result=UnifiedToolRound.compact(input);
        assertThat(result.has("completed")).isFalse();assertThat(result.has("summary")).isFalse();
        assertThat(result.path("evidence").size()).isEqualTo(1);
        assertThat(result.toString()).contains("Tests not run","Recorded change");
    }
    @Test void requestObservationReuseDoesNotLeakAcrossRequests() {
        var count=new java.util.concurrent.atomic.AtomicInteger();
        for(int request=0;request<2;request++)try(var trace=UnifiedRequestTrace.begin()) {
            int first=UnifiedRequestTrace.reuse("P:git",count::incrementAndGet);
            assertThat(UnifiedRequestTrace.reuse("P:git",count::incrementAndGet)).isEqualTo(first);
        }
        assertThat(count.get()).isEqualTo(2);
    }
    @Test void metricIntentDoesNotLogArbitraryModelText() {
        try(var trace=UnifiedRequestTrace.begin()) {
            trace.intent("private source text");
            assertThat(trace.snapshot().toString()).doesNotContain("private source text");
        }
    }
}
