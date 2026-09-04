package com.localai.workspace.agent;

import com.localai.workspace.discovery.DetectedProject;
import com.localai.workspace.discovery.ProjectDiscoveryService;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GitReadOnlyService {

    private final ProjectDiscoveryService projectDiscoveryService;
    private final GitProcessRunner processRunner;
    private final GitAgentProperties properties;

    public GitReadOnlyService(
            ProjectDiscoveryService projectDiscoveryService,
            GitProcessRunner processRunner,
            GitAgentProperties properties
    ) {
        this.projectDiscoveryService = projectDiscoveryService;
        this.processRunner = processRunner;
        this.properties = properties;
    }

    public GitStatusResult getStatus(String projectId) {
        Optional<DetectedProject> resolved = resolve(projectId);
        if (resolved.isEmpty()) {
            return statusFailure(projectId, GitToolStatus.PROJECT_NOT_FOUND, "Project ID was not found in the Workspace");
        }
        if (!resolved.get().gitRepository()) {
            return statusFailure(projectId, GitToolStatus.NOT_GIT_REPOSITORY, "Project is not a Git repository");
        }

        GitCommandResult command = processRunner.status(resolved.get().rootPath());
        if (!command.successful()) {
            return statusFailure(projectId, GitToolStatus.TOOL_FAILED, commandFailureReason(command));
        }
        return parseStatus(projectId, command.output());
    }

    public GitRecentCommitsResult getRecentCommits(String projectId, int requestedLimit) {
        int appliedLimit = Math.max(1, Math.min(requestedLimit, properties.maxRecentCommits()));
        Optional<DetectedProject> resolved = resolve(projectId);
        if (resolved.isEmpty()) {
            return commitsFailure(projectId, requestedLimit, appliedLimit, GitToolStatus.PROJECT_NOT_FOUND,
                    "Project ID was not found in the Workspace");
        }
        if (!resolved.get().gitRepository()) {
            return commitsFailure(projectId, requestedLimit, appliedLimit, GitToolStatus.NOT_GIT_REPOSITORY,
                    "Project is not a Git repository");
        }

        GitCommandResult command = processRunner.recentCommits(resolved.get().rootPath(), appliedLimit);
        if (!command.successful()) {
            return commitsFailure(projectId, requestedLimit, appliedLimit, GitToolStatus.TOOL_FAILED,
                    commandFailureReason(command));
        }

        List<RecentCommit> commits = new ArrayList<>();
        for (String record : command.output().split("\\u001e")) {
            String normalized = record.strip();
            if (normalized.isEmpty()) {
                continue;
            }
            String[] fields = normalized.split("\\u001f", -1);
            if (fields.length == 4) {
                commits.add(new RecentCommit(fields[0], fields[1], fields[2], fields[3]));
            }
        }
        return new GitRecentCommitsResult(
                projectId, GitToolStatus.SUCCESS, requestedLimit, appliedLimit, List.copyOf(commits), null
        );
    }

    public GitDiffSummaryResult getDiffSummary(String projectId) {
        Optional<DetectedProject> resolved = resolve(projectId);
        if (resolved.isEmpty()) {
            return diffFailure(projectId, GitToolStatus.PROJECT_NOT_FOUND, "Project ID was not found in the Workspace");
        }
        if (!resolved.get().gitRepository()) {
            return diffFailure(projectId, GitToolStatus.NOT_GIT_REPOSITORY, "Project is not a Git repository");
        }

        Path projectRoot = resolved.get().rootPath();
        GitCommandResult diffCommand = processRunner.diffSummary(projectRoot);
        GitCommandResult statusCommand = processRunner.status(projectRoot);
        if (!diffCommand.successful() || !statusCommand.successful()) {
            return diffFailure(projectId, GitToolStatus.TOOL_FAILED,
                    commandFailureReason(!diffCommand.successful() ? diffCommand : statusCommand));
        }

        List<GitDiffFileSummary> files = new ArrayList<>();
        int totalAdditions = 0;
        int totalDeletions = 0;
        for (String line : diffCommand.output().lines().toList()) {
            String[] fields = line.split("\\t", 3);
            if (fields.length != 3) {
                continue;
            }
            boolean binary = fields[0].equals("-") || fields[1].equals("-");
            Integer additions = binary ? null : parseCount(fields[0]);
            Integer deletions = binary ? null : parseCount(fields[1]);
            if (additions != null) {
                totalAdditions += additions;
            }
            if (deletions != null) {
                totalDeletions += deletions;
            }
            files.add(new GitDiffFileSummary(fields[2], additions, deletions, binary));
        }
        List<String> untracked = parseStatus(projectId, statusCommand.output()).untracked();
        return new GitDiffSummaryResult(
                projectId, GitToolStatus.SUCCESS, files.size() + untracked.size(), totalAdditions, totalDeletions,
                List.copyOf(files), untracked, null
        );
    }

    private Optional<DetectedProject> resolve(String projectId) {
        try {
            return projectDiscoveryService.findProject(projectDiscoveryService.defaultWorkspaceRoot(), projectId);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private GitStatusResult parseStatus(String projectId, String output) {
        String branch = "UNKNOWN";
        List<String> modified = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        List<String> untracked = new ArrayList<>();

        for (String line : output.lines().toList()) {
            if (line.startsWith("## ")) {
                branch = parseBranch(line.substring(3));
                continue;
            }
            if (line.length() < 3) {
                continue;
            }
            String path = line.substring(3);
            char indexStatus = line.charAt(0);
            char workTreeStatus = line.charAt(1);
            if (indexStatus == '?' && workTreeStatus == '?') {
                untracked.add(path);
            } else if (indexStatus == 'D' || workTreeStatus == 'D') {
                deleted.add(path);
            } else if (indexStatus == 'A' || workTreeStatus == 'A') {
                added.add(path);
            } else {
                modified.add(path);
            }
        }

        boolean clean = modified.isEmpty() && added.isEmpty() && deleted.isEmpty() && untracked.isEmpty();
        return new GitStatusResult(
                projectId, GitToolStatus.SUCCESS, branch, clean,
                List.copyOf(modified), List.copyOf(added), List.copyOf(deleted), List.copyOf(untracked), null
        );
    }

    private String parseBranch(String value) {
        if (value.startsWith("HEAD (no branch)")) {
            return "DETACHED_HEAD";
        }
        if (value.startsWith("No commits yet on ")) {
            return value.substring("No commits yet on ".length()).strip();
        }
        int trackingSeparator = value.indexOf("...");
        return (trackingSeparator >= 0 ? value.substring(0, trackingSeparator) : value).strip();
    }

    private Integer parseCount(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String commandFailureReason(GitCommandResult command) {
        if (command.timedOut()) {
            return "Git read operation timed out";
        }
        if (command.truncated()) {
            return "Git read result exceeded the safe output limit";
        }
        return "Git read operation failed";
    }

    private GitStatusResult statusFailure(String projectId, GitToolStatus status, String reason) {
        return new GitStatusResult(projectId, status, null, false,
                List.of(), List.of(), List.of(), List.of(), reason);
    }

    private GitRecentCommitsResult commitsFailure(
            String projectId, int requestedLimit, int appliedLimit, GitToolStatus status, String reason
    ) {
        return new GitRecentCommitsResult(projectId, status, requestedLimit, appliedLimit, List.of(), reason);
    }

    private GitDiffSummaryResult diffFailure(String projectId, GitToolStatus status, String reason) {
        return new GitDiffSummaryResult(projectId, status, 0, 0, 0, List.of(), List.of(), reason);
    }
}
