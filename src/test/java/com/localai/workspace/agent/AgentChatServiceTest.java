package com.localai.workspace.agent;

import com.localai.workspace.chat.ChatService;
import com.localai.workspace.rag.RagCitationValidator;
import com.localai.workspace.rag.RagContextAssemblyService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentChatServiceTest {
    final ChatService chat = mock(ChatService.class);
    final GitReadOnlyService git = mock(GitReadOnlyService.class);
    final DockerReadOnlyService docker = mock(DockerReadOnlyService.class);
    final DatabaseReadOnlyService db = mock(DatabaseReadOnlyService.class);
    final LogReadOnlyService logs = mock(LogReadOnlyService.class);
    final RagContextAssemblyService contexts = mock(RagContextAssemblyService.class);
    final AgentChatService service = new AgentChatService(chat, git, docker,
            mock(OllamaReadOnlyService.class), db, logs, contexts, new LogSecretRedactor(),
            new RagCitationValidator());

    @Test
    void recordsActualInterleavedOrderAndPreservesPartialResults() {
        when(db.getStatus()).thenReturn(new DatabaseStatusResult(LocalEnvironmentStatus.AVAILABLE, true, true, null));
        when(logs.getRecentErrors("Local_Ai_Work", 20)).thenThrow(new IllegalStateException("password=DO_NOT_EXPOSE"));
        when(git.getStatus("Local_Ai_Work")).thenReturn(new GitStatusResult("Local_Ai_Work",
                GitToolStatus.SUCCESS, "main", true, List.of(), List.of(), List.of(), List.of(), null));
        when(chat.chatWithToolCallbacks(anyString(), anyString(), any(ToolCallback[].class)))
                .thenAnswer(inv -> {
                    var callbacks = callbacks(inv.getArguments());
                    assertThat(call(callbacks, "getDatabaseStatus", "{}")).contains("AVAILABLE");
                    assertThat(call(callbacks, "getRecentErrors", "{\"projectId\":\"Local_Ai_Work\",\"limit\":20}"))
                            .contains("TOOL_FAILED").doesNotContain("DO_NOT_EXPOSE", "IllegalStateException");
                    assertThat(call(callbacks, "getGitStatus", "{\"projectId\":\"Local_Ai_Work\"}")).contains("main");
                    return "확인된 사실: DB 연결됨. 확인 한계: 로그 조회 실패. 추론: 원인 판단 불가.";
                });
        var result = service.chat(new AgentChatRequest("Local_Ai_Work", "환경, 로그, Git으로 진단해줘"));
        assertThat(result.toolsUsed()).containsExactly("getDatabaseStatus", "getRecentErrors", "getGitStatus");
        assertThat(result.toolCalls()).extracting(AgentToolCall::sequence).containsExactly(1, 2, 3);
        assertThat(result.status()).isEqualTo(AgentChatStatus.SUCCESS_WITH_WARNINGS);
        assertThat(result.warnings()).singleElement().asString().contains("getRecentErrors", "TOOL_FAILED");
        assertThat(result.answer()).contains("DB 연결됨");
        assertThat(result.totalDurationMillis()).isGreaterThanOrEqualTo(
                result.toolExecutionDurationMillis() + result.llmDurationMillis());
    }

    @Test
    void generalQuestionHasNoToolsAndPromptIncludesDiagnosisTrustBoundary() {
        when(chat.chatWithToolCallbacks(anyString(), anyString(), any(ToolCallback[].class)))
                .thenAnswer(inv -> {
                    assertThat((String) inv.getArgument(0)).contains("untrusted data", "not instructions",
                            "확인된 사실", "추론", "확인 한계", "Correlation is not causation",
                            "searchProjectKnowledge", "getRecentErrors", "getDockerStatus", "Redacted secrets");
                    assertThat(callbacks(inv.getArguments())).hasSize(12);
                    return "ArrayList는 크기를 조정할 수 있는 목록입니다.";
                });
        var result = service.chat(new AgentChatRequest("Local_Ai_Work", "Java ArrayList 설명해줘"));
        assertThat(result.status()).isEqualTo(AgentChatStatus.SUCCESS);
        assertThat(result.toolCalls()).isEmpty();
        verifyNoInteractions(git, docker, db, logs, contexts);
    }

    @Test
    void allToolsFailIsInsufficientAndModelFailureStillPreservesTrace() {
        when(db.getStatus()).thenThrow(new RuntimeException("private stack"));
        when(chat.chatWithToolCallbacks(anyString(), anyString(), any(ToolCallback[].class)))
                .thenAnswer(inv -> {
                    call(callbacks(inv.getArguments()), "getDatabaseStatus", "{}");
                    return "확인 한계: DB 상태 조회 실패. 현재 근거로 판단 불가.";
                });
        assertThat(service.chat(new AgentChatRequest("Local_Ai_Work", "DB 상태 확인")).status())
                .isEqualTo(AgentChatStatus.INSUFFICIENT_EVIDENCE);
        when(chat.chatWithToolCallbacks(anyString(), anyString(), any(ToolCallback[].class)))
                .thenAnswer(inv -> {
                    call(callbacks(inv.getArguments()), "getDatabaseStatus", "{}");
                    throw new RuntimeException("model private stack");
                });
        var result = service.chat(new AgentChatRequest("Local_Ai_Work", "DB 상태 확인"));
        assertThat(result.status()).isEqualTo(AgentChatStatus.LLM_FAILED);
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.warnings().toString()).contains("getDatabaseStatus").doesNotContain("private stack");
    }

    @Test
    void policyExplicitlyBoundsSyntheticFactsAndInference() {
        assertThat(AgentChatService.SYSTEM_PROMPT).contains(
                "entire database is problem-free",
                "cannot establish zero containers",
                "no matching entry was found within the inspected files and range",
                "do not prove that the commit caused the error",
                "do not guess why inspection failed",
                "returned citation ID such as [K1-S1]");
    }

    @Test
    void validatesAndReturnsKnowledgeCitationMetadata() {
        when(contexts.assemble(any())).thenReturn(ProjectKnowledgeAgentToolsTest.context("Local_Ai_Work"));
        when(chat.chatWithToolCallbacks(anyString(), anyString(), any(ToolCallback[].class)))
                .thenAnswer(inv -> {
                    call(callbacks(inv.getArguments()), "searchProjectKnowledge",
                            "{\"query\":\"type detection\"}");
                    return "Project type is detected from markers [K1-S1].";
                });

        var result = service.chat(new AgentChatRequest("Local_Ai_Work", "Explain type detection"));

        assertThat(result.status()).isEqualTo(AgentChatStatus.SUCCESS);
        assertThat(result.knowledgeSourceCount()).isEqualTo(1);
        assertThat(result.knowledgeSources()).singleElement().satisfies(source -> {
            assertThat(source.id()).isEqualTo("K1-S1");
            assertThat(source.filePath()).isEqualTo("src/A.java");
            assertThat(source.startLine()).isEqualTo(10);
            assertThat(source.endLine()).isEqualTo(12);
        });
        assertThat(result.usedSourceIds()).containsExactly("K1-S1");
        assertThat(result.invalidSourceIds()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void warnsForMissingAndUnavailableKnowledgeCitations() {
        when(contexts.assemble(any())).thenReturn(ProjectKnowledgeAgentToolsTest.context("Local_Ai_Work"));
        when(chat.chatWithToolCallbacks(anyString(), anyString(), any(ToolCallback[].class)))
                .thenAnswer(inv -> {
                    call(callbacks(inv.getArguments()), "searchProjectKnowledge", "{\"query\":\"type\"}");
                    return "Type detection uses markers.";
                });
        var missing = service.chat(new AgentChatRequest("Local_Ai_Work", "Explain type detection"));
        assertThat(missing.status()).isEqualTo(AgentChatStatus.SUCCESS_WITH_WARNINGS);
        assertThat(missing.warnings()).contains("Answer contains no knowledge Source citation despite available evidence");

        when(chat.chatWithToolCallbacks(anyString(), anyString(), any(ToolCallback[].class)))
                .thenAnswer(inv -> {
                    call(callbacks(inv.getArguments()), "searchProjectKnowledge", "{\"query\":\"type\"}");
                    return "Type detection uses markers [K1-S99].";
                });
        var invalid = service.chat(new AgentChatRequest("Local_Ai_Work", "Explain type detection"));
        assertThat(invalid.invalidSourceIds()).containsExactly("K1-S99");
        assertThat(invalid.warnings()).contains("Answer contains unavailable knowledge citations: K1-S99");
    }

    static ToolCallback[] callbacks(Object[] arguments) {
        return Arrays.stream(arguments).skip(2).flatMap(arg -> arg instanceof ToolCallback[] array
                ? Arrays.stream(array) : java.util.stream.Stream.of((ToolCallback) arg))
                .toArray(ToolCallback[]::new);
    }

    static String call(ToolCallback[] callbacks, String name, String input) {
        return Arrays.stream(callbacks).filter(c -> c.getToolDefinition().name().equals(name))
                .findFirst().orElseThrow().call(input);
    }
}
