package com.localai.workspace.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;

final class GitAgentTools implements AgentToolTracker {

    private final String allowedProjectId;
    private final GitReadOnlyService gitService;
    private final List<AgentToolInvocation> invocations = new ArrayList<>();
    private boolean failed;

    GitAgentTools(String allowedProjectId, GitReadOnlyService gitService) {
        this.allowedProjectId = allowedProjectId;
        this.gitService = gitService;
    }

    @Tool(
            name = "getGitStatus",
            description = "Read the current Git branch and working-tree status for the exact requested project. "
                    + "Use this for questions about current changes, modified files, untracked files, or cleanliness."
    )
    GitStatusResult getGitStatus(
            @ToolParam(description = "Exact projectId from the current user request") String projectId
    ) {
        long startedAt = System.nanoTime();
        GitStatusResult result = allowedProjectId.equals(projectId)
                ? gitService.getStatus(projectId)
                : new GitStatusResult(projectId, GitToolStatus.PROJECT_SCOPE_MISMATCH, null, false,
                List.of(), List.of(), List.of(), List.of(), "Tool projectId does not match the requested project");
        record("getGitStatus", startedAt, result.status());
        return result;
    }

    @Tool(
            name = "getRecentCommits",
            description = "Read recent Git commit metadata for the exact requested project. "
                    + "Use this for questions about recent work or recent commits. The safe maximum limit is enforced."
    )
    GitRecentCommitsResult getRecentCommits(
            @ToolParam(description = "Exact projectId from the current user request") String projectId,
            @ToolParam(description = "Number of recent commits requested; use 5 when the user gives no number") int limit
    ) {
        long startedAt = System.nanoTime();
        GitRecentCommitsResult result = allowedProjectId.equals(projectId)
                ? gitService.getRecentCommits(projectId, limit)
                : new GitRecentCommitsResult(projectId, GitToolStatus.PROJECT_SCOPE_MISMATCH,
                limit, 0, List.of(), "Tool projectId does not match the requested project");
        record("getRecentCommits", startedAt, result.status());
        return result;
    }

    @Tool(
            name = "getGitDiffSummary",
            description = "Read only a bounded Git diff summary for the exact requested project, including changed "
                    + "file names and addition/deletion counts. Never returns the full diff body."
    )
    GitDiffSummaryResult getGitDiffSummary(
            @ToolParam(description = "Exact projectId from the current user request") String projectId
    ) {
        long startedAt = System.nanoTime();
        GitDiffSummaryResult result = allowedProjectId.equals(projectId)
                ? gitService.getDiffSummary(projectId)
                : new GitDiffSummaryResult(projectId, GitToolStatus.PROJECT_SCOPE_MISMATCH,
                0, 0, 0, List.of(), List.of(), "Tool projectId does not match the requested project");
        record("getGitDiffSummary", startedAt, result.status());
        return result;
    }

    public List<AgentToolInvocation> invocations() {
        return List.copyOf(invocations);
    }

    public boolean failed() {
        return failed;
    }

    private void record(String toolName, long startedAt, GitToolStatus status) {
        invocations.add(new AgentToolInvocation(toolName, (System.nanoTime() - startedAt) / 1_000_000));
        if (status != GitToolStatus.SUCCESS) {
            failed = true;
        }
    }
}
