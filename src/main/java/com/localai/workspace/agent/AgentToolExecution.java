package com.localai.workspace.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One instance per request; observes actual callbacks, including unexpected Tool failures. */
final class AgentToolExecution {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> OBSERVED = Set.of(
            "SUCCESS", "AVAILABLE", "NO_LOG_FILES", "NO_RESULTS", "NOT_GIT_REPOSITORY");
    private final List<AgentToolCall> calls = new ArrayList<>();
    private final List<JsonNode> arguments = new ArrayList<>();

    ToolCallback wrap(ToolCallback delegate) {
        return new ToolCallback() {
            public ToolDefinition getToolDefinition() { return delegate.getToolDefinition(); }
            public ToolMetadata getToolMetadata() { return delegate.getToolMetadata(); }
            public String call(String input) { return call(input, null); }
            public String call(String input, ToolContext context) {
                return execute(delegate, input, context);
            }
        };
    }

    private synchronized String execute(ToolCallback delegate, String input, ToolContext context) {
        long started = System.nanoTime();
        String name = delegate.getToolDefinition().name();
        Integer duplicate = null;
        String output;
        String outcome;
        boolean successful = false;
        JsonNode parsedInput = null;
        try {
            parsedInput = JSON.readTree(input);
            for (int i = 0; i < calls.size(); i++) {
                if (calls.get(i).toolName().equals(name) && parsedInput.equals(arguments.get(i))) {
                    duplicate = calls.get(i).sequence();
                    break;
                }
            }
            output = delegate.call(input, context);
            JsonNode result = JSON.readTree(output);
            outcome = result.path("status").asText("FAILED");
            // Status is metadata, never pass arbitrary result text into warnings or telemetry.
            if (!outcome.matches("[A-Z_]{1,60}")) {
                outcome = "FAILED";
            }
            successful = OBSERVED.contains(outcome);
            if (!successful) {
                // A failed DTO's default 0/false/[] values are not observations.
                output = JSON.createObjectNode().put("status", outcome)
                        .put("evidenceAvailable", false)
                        .put("reason", new LogSecretRedactor().redact(
                                result.path("reason").asText("Inspection failed")))
                        .put("limitation", "No current data was obtained. Container count, health, and extension "
                                + "availability are UNKNOWN; do not interpret missing values as absent/disabled.")
                        .toString();
            } else if (result.path("failedFiles").asInt(0) > 0) {
                outcome = "PARTIAL_SUCCESS";
            }
        } catch (Exception exception) {
            outcome = "TOOL_FAILED";
            successful = false;
            output = "{\"status\":\"TOOL_FAILED\",\"evidenceAvailable\":false,"
                    + "\"reason\":\"Inspection failed; cause not determined. "
                    + "Retain other Tool results and report this evidence gap.\"}";
        }
        calls.add(new AgentToolCall(calls.size() + 1, name,
                (System.nanoTime() - started) / 1_000_000, outcome, successful, duplicate));
        arguments.add(parsedInput);
        return output;
    }

    synchronized List<AgentToolCall> calls() { return List.copyOf(calls); }

    List<String> warnings() {
        return calls().stream().filter(call -> !call.successful()
                        || call.outcome().equals("PARTIAL_SUCCESS") || call.sameArgumentsAs() != null)
                .map(call -> call.toolName() + " (#" + call.sequence() + "): "
                        + (!call.successful() || call.outcome().equals("PARTIAL_SUCCESS")
                        ? call.outcome() : "repeated identical arguments"))
                .toList();
    }

    AgentChatStatus status() {
        List<AgentToolCall> snapshot = calls();
        if (!snapshot.isEmpty() && snapshot.stream().noneMatch(AgentToolCall::successful)) {
            return AgentChatStatus.INSUFFICIENT_EVIDENCE;
        }
        return warnings().isEmpty() ? AgentChatStatus.SUCCESS : AgentChatStatus.SUCCESS_WITH_WARNINGS;
    }
}
