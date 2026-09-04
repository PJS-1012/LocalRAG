package com.localai.workspace.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;

final class LogAgentTools implements AgentToolTracker {

    private final String allowedProjectId;
    private final LogReadOnlyService logService;
    private final List<AgentToolInvocation> invocations = new ArrayList<>();
    private boolean failed;

    LogAgentTools(String allowedProjectId, LogReadOnlyService logService) {
        this.allowedProjectId = allowedProjectId;
        this.logService = logService;
    }

    @Tool(
            name = "getRecentLogs",
            description = "Read recent sanitized log entries from the exact requested Project's bounded allowed log "
                    + "locations. Use for current or recent general log questions. Use 20 when no limit is requested."
    )
    LogInspectionResult getRecentLogs(
            @ToolParam(description = "Exact projectId from the current user request") String projectId,
            @ToolParam(description = "Maximum recent entries requested; use 20 when unspecified") int limit
    ) {
        return invoke("getRecentLogs", projectId, () -> logService.getRecentLogs(projectId, limit));
    }

    @Tool(
            name = "getRecentErrors",
            description = "Read recent sanitized ERROR, FATAL, Exception, and Caused-by entries for broad questions "
                    + "such as whether any recent errors exist. Do not use when the user names a specific error, "
                    + "exception class, or phrase; use searchLogs for every named term."
    )
    LogInspectionResult getRecentErrors(
            @ToolParam(description = "Exact projectId from the current user request") String projectId,
            @ToolParam(description = "Maximum matching entries requested; use 20 when unspecified") int limit
    ) {
        return invoke("getRecentErrors", projectId, () -> logService.getRecentErrors(projectId, limit));
    }

    @Tool(
            name = "searchLogs",
            description = "Search the exact requested Project's sanitized bounded log tail using literal text only, "
                    + "never a regular expression or shell pattern. Always use this Tool when the user names a "
                    + "specific error, exception class such as NullPointerException, or exact log phrase."
    )
    LogInspectionResult searchLogs(
            @ToolParam(description = "Exact projectId from the current user request") String projectId,
            @ToolParam(description = "Literal case-insensitive text to find; never a regular expression") String query,
            @ToolParam(description = "Maximum matching entries requested; use 20 when unspecified") int limit
    ) {
        return invoke("searchLogs", projectId, () -> logService.searchLogs(projectId, query, limit));
    }

    @Override
    public List<AgentToolInvocation> invocations() {
        return List.copyOf(invocations);
    }

    @Override
    public boolean failed() {
        return failed;
    }

    private LogInspectionResult invoke(String toolName, String projectId, LogOperation operation) {
        long startedAt = System.nanoTime();
        LogInspectionResult result = allowedProjectId.equals(projectId)
                ? operation.execute()
                : new LogInspectionResult(
                projectId, LogToolStatus.PROJECT_SCOPE_MISMATCH, 0, 0, 0, 0,
                false, 0, 0, 0, List.of(), "Tool projectId does not match the requested project"
        );
        invocations.add(new AgentToolInvocation(toolName, (System.nanoTime() - startedAt) / 1_000_000));
        if (result.status() != LogToolStatus.SUCCESS && result.status() != LogToolStatus.NO_LOG_FILES) {
            failed = true;
        }
        return result;
    }

    @FunctionalInterface
    private interface LogOperation {
        LogInspectionResult execute();
    }
}
