package com.localai.workspace.agent;

import com.localai.workspace.errors.*;
import com.localai.workspace.workflow.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

final class DeveloperWorkflowAgentTools {
    private final String allowedProjectId;
    private final ErrorSimilarityService similarities;
    private final ProjectProgressService progress;
    private final DevelopmentActivityService activity;
    private boolean evidenceOnly;
    DeveloperWorkflowAgentTools evidenceOnly() { this.evidenceOnly=true; return this; }

    DeveloperWorkflowAgentTools(String allowedProjectId, ErrorSimilarityService similarities,
            ProjectProgressService progress, DevelopmentActivityService activity) {
        this.allowedProjectId=allowedProjectId; this.similarities=similarities;
        this.progress=progress; this.activity=activity;
    }

    @Tool(name="findSimilarErrors", description="Find semantically similar explicitly saved Error History entries "
            + "inside the exact project. Use only when the user asks whether a current error resembles a past error. "
            + "Similarity is a candidate, never proof of the current cause.")
    ErrorSimilarityResponse findSimilarErrors(
            @ToolParam(description="Exact projectId from the current request") String projectId,
            @ToolParam(description="Optional error or exception type", required=false) String errorType,
            @ToolParam(description="Current error message") String errorMessage,
            @ToolParam(description="Optional observed symptom", required=false) String symptom,
            @ToolParam(description="Maximum candidates, default 5", required=false) Integer topK) {
        requireScope(projectId);
        return similarities.find(new ErrorSimilarRequest(projectId,errorType,errorMessage,symptom,topK));
    }

    @Tool(name="analyzeProjectProgress", description="Analyze the current project state from bounded Git, indexed "
            + "project knowledge, and Error History evidence. Use for where the project is now, completed work, "
            + "remaining work, blockers, or documentation mismatch. Never invent a percentage.")
    ProjectProgressResponse analyzeProjectProgress(
            @ToolParam(description="Exact projectId from the current request") String projectId) {
        requireScope(projectId);
        return evidenceOnly ? progress.collect(new ProjectProgressRequest(projectId))
                : progress.analyze(new ProjectProgressRequest(projectId));
    }

    @Tool(name="summarizeRecentDevelopment", description="Summarize recent development from Git evidence and "
            + "related indexed Decision Logs. Use for recent work, today's work, last 24 hours, last 7 days, "
            + "or recent commit summaries.")
    DevelopmentActivityResponse summarizeRecentDevelopment(
            @ToolParam(description="Exact projectId from the current request") String projectId,
            @ToolParam(description="RECENT, TODAY, LAST_24_HOURS, or LAST_7_DAYS", required=false) String timeRange,
            @ToolParam(description="Commit limit 1-20; use 5 when omitted", required=false) Integer commitLimit) {
        requireScope(projectId);
        var request=new DevelopmentActivityRequest(projectId,since(timeRange),commitLimit);
        return evidenceOnly ? activity.collect(request) : activity.summarize(request);
    }

    private Instant since(String timeRange) {
        if(timeRange==null || timeRange.isBlank() || timeRange.equalsIgnoreCase("RECENT")) return null;
        Instant now=Instant.now();
        return switch(timeRange.toUpperCase(Locale.ROOT)) {
            case "TODAY" -> ZonedDateTime.now(ZoneId.systemDefault()).toLocalDate()
                    .atStartOfDay(ZoneId.systemDefault()).toInstant();
            case "LAST_24_HOURS" -> now.minus(24,ChronoUnit.HOURS);
            case "LAST_7_DAYS" -> now.minus(7,ChronoUnit.DAYS);
            default -> throw new IllegalArgumentException("Unsupported timeRange");
        };
    }

    private void requireScope(String projectId) {
        if(!allowedProjectId.equals(projectId)) throw new IllegalArgumentException(
                "Tool projectId does not match the requested project");
    }
}
