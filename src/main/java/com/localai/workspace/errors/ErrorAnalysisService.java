package com.localai.workspace.errors;

import com.fasterxml.jackson.databind.*;
import com.localai.workspace.agent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.springframework.http.HttpStatus.*;

@Service
public class ErrorAnalysisService {
    private final AgentChatService agent;
    private final ErrorProjectScope scope;
    private final LogSecretRedactor redactor;
    private final ObjectMapper json;
    private final Duration ttl;
    private final int maxDrafts;
    // Encoded server snapshots cannot be mutated through response object references.
    private final Map<UUID, Draft> drafts = new LinkedHashMap<>();
    private record Draft(Instant expiresAt, String projectId, String snapshot) { }
    private static final Pattern ERROR = Pattern.compile("\\b([A-Za-z_$][\\w.$]*(?:Exception|Error))\\b");

    public ErrorAnalysisService(AgentChatService agent, ErrorProjectScope scope, LogSecretRedactor redactor,
            ObjectMapper json, @Value("${localrag.errors.draft-ttl:PT30M}") Duration ttl,
            @Value("${localrag.errors.max-drafts:100}") int maxDrafts) {
        this.agent = agent; this.scope = scope; this.redactor = redactor; this.json = json;
        if (ttl.isNegative() || ttl.isZero() || maxDrafts < 1) throw new IllegalArgumentException("Invalid draft limits");
        this.ttl = ttl; this.maxDrafts = maxDrafts;
    }

    public ErrorAnalysisResult analyzeError(ErrorAnalysisRequest request) {
        long started = System.nanoTime();
        String project = scope.require(request.projectId());
        var run = agent.analyzeError(new AgentChatRequest(project, redactor.redact(request.query())));
        var response = run.response();
        var evidence = new ArrayList<ToolEvidence>();
        var unknown = new ArrayList<String>();
        var paths = new LinkedHashSet<String>();
        var commits = new LinkedHashSet<String>();
        String message = null, type = null;
        Instant occurred = null;
        for (var item : run.evidence()) {
            JsonNode data = sanitize(item.result());
            // Refuse an unexpected cross-project DTO even if a Tool wrapper was bypassed.
            if (data.hasNonNull("projectId") && !project.equals(data.path("projectId").asText())) {
                unknown.add(item.toolName() + ": project scope mismatch");
                continue;
            }
            evidence.add(new ToolEvidence(item.sequence(), item.toolName(), item.observedAt(), data));
            String state = data.path("status").asText();
            boolean usable = Set.of("SUCCESS", "AVAILABLE", "NO_LOG_FILES", "NO_RESULTS", "NOT_GIT_REPOSITORY").contains(state);
            if (!usable || data.path("failedFiles").asInt() > 0) unknown.add(item.toolName() + ": incomplete inspection");
            if (!usable) continue;
            if (Set.of("getRecentErrors", "searchLogs", "getRecentLogs").contains(item.toolName())) {
                for (var entry : data.path("entries")) {
                    addPath(paths, entry.path("sourceFile").asText());
                    String text = entry.path("message").asText("");
                    var matcher = ERROR.matcher(text);
                    boolean exception = matcher.find();
                    if (message == null && (exception || Set.of("ERROR", "FATAL").contains(entry.path("level").asText()))) {
                        message = text; type = exception ? matcher.group(1) : entry.path("level").asText();
                        try { occurred = Instant.parse(entry.path("timestamp").asText()); }
                        catch (RuntimeException ignored) { /* no timezone guessing */ }
                    }
                }
            }
            if (item.toolName().equals("searchProjectKnowledge"))
                for (var source : data.path("sources")) addPath(paths, source.path("path").asText());
            if (item.toolName().equals("getRecentCommits"))
                for (var commit : data.path("commits")) {
                    String hash = commit.path("hash").asText();
                    if (hash.matches("[a-fA-F0-9]{7,40}")) commits.add(hash);
                }
            if (item.toolName().equals("getGitDiffSummary"))
                for (var file : data.path("files")) addPath(paths, file.path("filePath").asText());
        }
        if (message == null) unknown.add("No matching error observed in the inspected scope; occurrence and cause unconfirmed.");
        if (evidence.stream().noneMatch(e -> e.toolName().equals("searchProjectKnowledge") && !e.result().path("sources").isEmpty()))
            unknown.add("No implementation Source was retrieved.");
        unknown.add("Root cause and solution have not been independently verified; commit chronology is not causation.");
        if (run.evidence().size() < response.toolCalls().size()) unknown.add("Evidence capture limit reached.");
        Instant now = Instant.now();
        long toolMs = response.toolExecutionDurationMillis();
        long ragMs = response.toolCalls().stream().filter(c -> c.toolName().equals("searchProjectKnowledge"))
                .mapToLong(AgentToolCall::durationMillis).sum();
        String narrative = message == null
                ? "조회 범위에서 일치하는 오류 근거를 확인하지 못했습니다. 원인을 판단할 수 없습니다."
                : redactor.redact(response.answer());
        // The raw answer remains unverified; it never populates rootCause, relatedFiles or relatedCommits.
        var result = new ErrorAnalysisResult(UUID.randomUUID(), project, now, now.plus(ttl), occurred, type,
                message, redactor.redact(request.query()), narrative, null, null, ErrorStatus.UNVERIFIED,
                message == null ? "NO_ERROR_EVIDENCE" : "ERROR_OBSERVED",
                List.copyOf(evidence), List.copyOf(unknown), List.copyOf(paths), List.copyOf(commits),
                sanitizeResponse(response, project, request.query(), narrative),
                toolMs, toolMs, ragMs, response.llmDurationMillis(), (System.nanoTime()-started)/1_000_000);
        remember(result);
        return result;
    }

    private AgentChatResponse sanitizeResponse(AgentChatResponse r, String project, String query, String answer) {
        return new AgentChatResponse(project, redactor.redact(query), answer, r.toolsUsed(),
                r.toolExecutionDurationMillis(), r.llmDurationMillis(), r.totalDurationMillis(), r.status(),
                r.warnings().stream().map(redactor::redact).toList(), r.toolCalls(), r.knowledgeSourceCount(),
                r.knowledgeSources().stream().map(s -> new AgentKnowledgeSource(s.id(), redactor.redact(s.filePath()),
                        s.startLine(), s.endLine())).toList(), r.usedSourceIds(), r.invalidSourceIds());
    }

    private void addPath(Set<String> paths, String value) {
        String path = redactor.redact(value).replace('\\', '/');
        if (path.isBlank() || path.startsWith("/") || path.contains(":")
                || Arrays.stream(path.split("/", -1)).anyMatch(p -> p.isBlank() || p.equals(".") || p.equals(".."))) return;
        paths.add(path);
    }

    JsonNode sanitize(JsonNode node) {
        if (node.isTextual()) return json.getNodeFactory().textNode(redactor.redact(node.asText()));
        if (node.isObject()) {
            var result = json.createObjectNode();
            node.fields().forEachRemaining(f -> {
                if (f.getKey().matches("(?i).*(password|passwd|pwd|token|secret|api.?key|authorization|cookie|credential).*"))
                    result.put(f.getKey(), "****");
                else result.set(f.getKey(), sanitize(f.getValue()));
            });
            return result;
        }
        if (node.isArray()) { var result = json.createArrayNode(); node.forEach(n -> result.add(sanitize(n))); return result; }
        return node.deepCopy();
    }

    private synchronized void remember(ErrorAnalysisResult result) {
        drafts.values().removeIf(d -> !d.expiresAt().isAfter(Instant.now()));
        if (drafts.size() >= maxDrafts) drafts.remove(drafts.keySet().iterator().next());
        try {
            String snapshot = json.writeValueAsString(result);
            if (snapshot.length() > 512_000) throw new ResponseStatusException(PAYLOAD_TOO_LARGE, "Analysis evidence too large");
            drafts.put(result.analysisId(), new Draft(result.expiresAt(), result.projectId(), snapshot));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot encode analysis"); }
    }

    public synchronized ErrorAnalysisResult draft(UUID id, String project) {
        Draft draft = drafts.get(id);
        if (draft == null || !draft.projectId().equals(project))
            throw new ResponseStatusException(NOT_FOUND, "Analysis not found for project");
        if (!draft.expiresAt().isAfter(Instant.now())) {
            drafts.remove(id); throw new ResponseStatusException(GONE, "Analysis expired; analyze again");
        }
        try { return json.readValue(draft.snapshot(), ErrorAnalysisResult.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot decode analysis"); }
    }
}
