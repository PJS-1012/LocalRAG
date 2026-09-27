package com.localai.workspace.chat;

import java.util.*;
import java.util.function.Supplier;

/** Request-local numeric profiling. Never retains prompts, source contents or model answers. */
public final class UnifiedRequestTrace implements AutoCloseable {
    private static final ThreadLocal<UnifiedRequestTrace> CURRENT = new ThreadLocal<>();
    private final UnifiedRequestTrace previous;
    private final long started = System.nanoTime();
    private final String requestId = UUID.randomUUID().toString();
    private final List<ModelCall> calls = new ArrayList<>();
    private final Map<String, Long> stages = new LinkedHashMap<>();
    private final Map<String,Object> observations=new HashMap<>();
    @SuppressWarnings("unchecked")
    public static <T> T reuse(String key,Supplier<T> read) {
        var trace=CURRENT.get();
        if(trace==null)return read.get();
        if(trace.observations.containsKey(key))return (T)trace.observations.get(key);
        T value=read.get();
        trace.observations.put(key,value);
        return value;
    }
    private String projectId;
    private String intent="GENERAL_OR_UNRESOLVED";
    public void projectId(String value) { projectId=value; }
    public String projectId() { return projectId; }
    public void intent(String value) {
        intent=Set.of("CODE_SPECIFIC","PROJECT_OVERVIEW","ONBOARDING","HANDOVER",
                "GENERAL_OR_UNRESOLVED","TOOL_STATUS_OR_WORKFLOW").contains(value)?value:"GENERAL_OR_UNRESOLVED";
    }

    private UnifiedRequestTrace() {
        previous = CURRENT.get();
        CURRENT.set(this);
    }
    public static UnifiedRequestTrace begin() { return new UnifiedRequestTrace(); }
    public static UnifiedRequestTrace current() { return CURRENT.get(); }
    public static <T> T measure(String stage, Supplier<T> action) {
        long start = System.nanoTime();
        try { return action.get(); }
        finally { recordStage(stage, (System.nanoTime() - start) / 1_000_000); }
    }
    public static void recordStage(String stage, long millis) {
        var trace = CURRENT.get();
        if (trace != null) trace.stages.merge(stage, millis, Long::sum);
    }
    public void addCall(String purpose, String model, long inputChars, long toolSchemaChars,
            long outputChars, long thinkingChars, long millis, boolean success,
            long inputTokens, long outputTokens, long loadMs, long prefillMs, long generationMs,
            String finishReason) {
        calls.add(new ModelCall(calls.size() + 1, purpose, model, inputChars, toolSchemaChars,
                outputChars, thinkingChars, millis, success, inputTokens, outputTokens,
                loadMs, prefillMs, generationMs, finishReason));
    }
    public record ModelCall(int callNumber, String purpose, String model, long inputContextChars,
            long toolSchemaChars, long outputChars, long thinkingChars, long durationMs, boolean success,
            long inputTokens, long outputTokens, long modelLoadMs, long promptEvaluationMs,
            long generationMs, String finishReason) {}
    public record Snapshot(String requestId, int llmCallCount, long llmMs, long routingIntentMs,
            long queryDecompositionMs, String decompositionMode, long evidenceCollectionMs,
            long vectorSearchMs, long queryEmbeddingMs, long toolExecutionMs,
            long progressActivityMs, long finalLlmGenerationMs, long totalMs,
            List<ModelCall> llmCalls, Map<String, Long> stageMs, String timingNotes,String intent) {}
    public Snapshot snapshot() {
        long llm = calls.stream().mapToLong(ModelCall::durationMs).sum();
        long routing = calls.stream().filter(c -> c.purpose().equals("TOOL_SELECTION"))
                .mapToLong(ModelCall::durationMs).sum();
        long generation = calls.stream().filter(c -> c.purpose().equals("FINAL_ANSWER"))
                .mapToLong(ModelCall::durationMs).sum();
        return new Snapshot(requestId, calls.size(), llm, routing,
                stages.getOrDefault("QUERY_DECOMPOSITION", 0L), "NO_SEPARATE_DECOMPOSITION_CALL",
                stages.getOrDefault("TOOL_EXECUTION", 0L),
                stages.getOrDefault("VECTOR_SEARCH", 0L), stages.getOrDefault("QUERY_EMBEDDING", 0L),
                stages.getOrDefault("TOOL_EXECUTION", 0L),
                stages.getOrDefault("PROGRESS_ACTIVITY", 0L), generation,
                (System.nanoTime() - started) / 1_000_000, List.copyOf(calls), Map.copyOf(stages),
                "Actual non-streaming Ollama HTTP attempts, including retries. Stage subtotals overlap: "
                + "vector/embedding/workflow are inside Tool evidence collection; do not add all columns. "
                + "Input chars are message text/history; tool schemas reported separately. Token counts are provider values.",intent);
    }
    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
