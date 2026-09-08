package com.localai.workspace.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentToolExecutionTest {
    @Test
    void detectsEquivalentJsonArgumentsWithoutCachingOrLeakingThem() {
        var delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(ToolDefinition.builder().name("searchLogs")
                .description("test").inputSchema("{}").build());
        when(delegate.call(anyString(), isNull())).thenReturn("{\"status\":\"SUCCESS\"}");
        var execution = new AgentToolExecution();
        var callback = execution.wrap(delegate);
        callback.call("{\"query\":\"private-value\",\"limit\":1}");
        callback.call("{\"limit\":1, \"query\":\"private-value\"}");
        callback.call("{\"query\":\"different\",\"limit\":1}");
        assertThat(execution.calls()).extracting(AgentToolCall::sameArgumentsAs).containsExactly(null, 1, null);
        assertThat(execution.calls().toString()).doesNotContain("private-value", "different");
        assertThat(execution.status()).isEqualTo(AgentChatStatus.SUCCESS_WITH_WARNINGS);
        verify(delegate, times(3)).call(anyString(), isNull());
    }

    @Test
    void nonRepositoryAndEmptyLogsAreObservationsNotInventedFailures() {
        var delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(ToolDefinition.builder().name("getGitStatus")
                .description("test").inputSchema("{}").build());
        when(delegate.call(anyString(), isNull())).thenReturn("{\"status\":\"NOT_GIT_REPOSITORY\"}");
        var execution = new AgentToolExecution();
        execution.wrap(delegate).call("{}");
        assertThat(execution.status()).isEqualTo(AgentChatStatus.SUCCESS);
        assertThat(execution.calls().get(0).outcome()).isEqualTo("NOT_GIT_REPOSITORY");
    }

    @Test
    void failedDtoDefaultsAreNotPassedToTheModelAsFacts() {
        var delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(ToolDefinition.builder().name("getDatabaseStatus")
                .description("test").inputSchema("{}").build());
        when(delegate.call(anyString(), isNull())).thenReturn(
                "{\"status\":\"NOT_RUNNING\",\"reachable\":false,\"pgvectorAvailable\":false,\"reason\":\"unreachable\"}");
        var execution = new AgentToolExecution();
        String result = execution.wrap(delegate).call("{}");
        assertThat(result).contains("NOT_RUNNING", "UNKNOWN", "unreachable")
                .doesNotContain("\"pgvectorAvailable\":false", "\"reachable\":false");
        assertThat(execution.status()).isEqualTo(AgentChatStatus.INSUFFICIENT_EVIDENCE);
    }

    @Test
    void partialLogFileFailureKeepsEntriesAndRaisesWarning() {
        var delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(ToolDefinition.builder().name("getRecentErrors")
                .description("test").inputSchema("{}").build());
        when(delegate.call(anyString(), isNull())).thenReturn(
                "{\"status\":\"SUCCESS\",\"failedFiles\":1,\"entries\":[\"sanitized error\"]}");
        var execution = new AgentToolExecution();
        assertThat(execution.wrap(delegate).call("{}")).contains("sanitized error");
        assertThat(execution.status()).isEqualTo(AgentChatStatus.SUCCESS_WITH_WARNINGS);
        assertThat(execution.warnings()).singleElement().asString().contains("PARTIAL_SUCCESS");
    }

    @Test
    void capturesOnlyValidKnowledgeCitationMetadata() {
        var delegate = mock(ToolCallback.class);
        when(delegate.getToolDefinition()).thenReturn(ToolDefinition.builder()
                .name("searchProjectKnowledge").description("test").inputSchema("{}").build());
        when(delegate.call(anyString(), isNull())).thenReturn("""
                {"status":"SUCCESS","sources":[
                  {"citationId":"K1-S1","path":"src/A.java","startLine":4,"endLine":8,"content":"secret body"},
                  {"citationId":"invalid","path":"src/B.java","startLine":1,"endLine":2}
                ]}
                """);
        var execution = new AgentToolExecution();
        execution.wrap(delegate).call("{}");

        assertThat(execution.knowledgeSources()).containsExactly(
                new AgentKnowledgeSource("K1-S1", "src/A.java", 4, 8));
        assertThat(execution.knowledgeSources().toString()).doesNotContain("secret body");
    }
}
