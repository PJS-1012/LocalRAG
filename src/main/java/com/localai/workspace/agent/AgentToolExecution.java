package com.localai.workspace.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private final List<AgentKnowledgeSource> knowledgeSources = new ArrayList<>();

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
                        .put("answerBoundary", "Report only the failed inspection and UNKNOWN cause. Do not add "
                                + "possible causes, commands, recommendations, or values omitted from this result.")
                        .toString();
            } else if (result.path("failedFiles").asInt(0) > 0) {
                outcome = "PARTIAL_SUCCESS";
            }
            if (successful && name.equals("searchProjectKnowledge")) {
                captureKnowledgeSources(result.path("sources"));
            }
            if (successful) {
                output = annotateEvidenceScope(name, outcome, result);
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

    synchronized List<AgentKnowledgeSource> knowledgeSources() { return List.copyOf(knowledgeSources); }

    private void captureKnowledgeSources(JsonNode sources) {
        if (!sources.isArray()) {
            return;
        }
        for (JsonNode source : sources) {
            String id = source.path("citationId").asText("");
            if (!id.matches("K\\d+-S\\d+")) {
                continue;
            }
            knowledgeSources.add(new AgentKnowledgeSource(
                    id,
                    source.path("path").asText(""),
                    source.path("startLine").asInt(),
                    source.path("endLine").asInt()
            ));
        }
    }


    private String annotateEvidenceScope(String toolName, String outcome, JsonNode result) {
        if (!(result instanceof ObjectNode object)) {
            return result.toString();
        }
        ObjectNode annotated = object.deepCopy();
        annotated.put("evidenceAvailable", true);
        String boundary = switch (toolName) {
            case "getDatabaseStatus" ->
                    "Only the current connection check and pgvector availability check are observed. "
                    + "Do not conclude that the entire database is healthy or identify a root cause.";
            case "getDockerStatus" ->
                    "Only Docker Engine reachability and reported version/state are observed.";
            case "getDockerContainers", "getProjectContainerStatus" ->
                    "Container state, count, and exit code do not establish why a container stopped. "
                    + "Do not infer network, configuration, permission, socket, or internal errors.";
            case "getRecentLogs", "getRecentErrors", "searchLogs" ->
                    outcome.equals("NO_LOG_FILES")
                            ? "No permitted log file was found in the configured inspection scope. "
                              + "This is not a Tool failure and does not prove that no errors exist."
                            : "Only entries returned from the bounded permitted log scan are observed.";
            case "getRecentCommits", "getGitDiffSummary", "getGitStatus" ->
                    "Git chronology or changed paths do not establish causation with an error.";
            case "searchProjectKnowledge" ->
                    "Indexed snapshot only. Cite factual implementation claims with the returned citationId values.";
            default -> "Use only fields explicitly returned by this Tool.";
        };
        annotated.put("answerBoundary", boundary);
        return annotated.toString();
    }
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
