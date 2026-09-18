package com.localai.workspace.agent;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
class GitProcessRunner {

    private final GitAgentProperties properties;

    GitProcessRunner(GitAgentProperties properties) {
        this.properties = properties;
    }

    GitCommandResult status(Path projectRoot) {
        return run(projectRoot, List.of("status", "--porcelain=v1", "--branch", "--untracked-files=all"));
    }

    GitCommandResult recentCommits(Path projectRoot, int limit) {
        return run(projectRoot, List.of(
                "log", "-n", Integer.toString(limit),
                "--pretty=format:%H%x1f%s%x1f%an%x1f%aI%x1e"
        ));
    }

    GitCommandResult diffSummary(Path projectRoot) {
        return run(projectRoot, List.of("diff", "--numstat", "HEAD", "--"));
    }

    GitCommandResult upstream(Path root) { return run(root, List.of("rev-parse", "--verify", "@{upstream}")); }
    GitCommandResult divergence(Path root, String upstreamSha) { return run(root, List.of("rev-list", "--left-right", "--count", "HEAD..." + upstreamSha)); }
    GitCommandResult isAncestor(Path root, String sha, String upstreamSha) { return run(root, List.of("merge-base", "--is-ancestor", sha, upstreamSha)); }

    private GitCommandResult run(Path projectRoot, List<String> gitArguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-c");
        command.add("core.quotepath=false");
        command.addAll(gitArguments);

        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(projectRoot.toFile())
                    .redirectErrorStream(true);
            processBuilder.environment().put("GIT_OPTIONAL_LOCKS", "0");
            processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
            process = processBuilder.start();
            Process runningProcess = process;
            CompletableFuture<BoundedOutput> outputFuture = CompletableFuture.supplyAsync(
                    () -> readBounded(runningProcess)
            );

            boolean finished = process.waitFor(properties.commandTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
            }
            BoundedOutput output = outputFuture.join();
            return new GitCommandResult(
                    finished ? process.exitValue() : -1,
                    output.value(),
                    !finished,
                    output.truncated()
            );
        } catch (IOException exception) {
            return new GitCommandResult(-1, "", false, false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return new GitCommandResult(-1, "", true, false);
        }
    }

    private BoundedOutput readBounded(Process process) {
        StringBuilder output = new StringBuilder();
        boolean truncated = false;
        char[] buffer = new char[4_096];
        try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                int remaining = properties.maxOutputCharacters() - output.length();
                if (remaining > 0) {
                    output.append(buffer, 0, Math.min(read, remaining));
                }
                if (read > remaining) {
                    truncated = true;
                }
            }
        } catch (IOException exception) {
            return new BoundedOutput("", true);
        }
        return new BoundedOutput(output.toString(), truncated);
    }

    private record BoundedOutput(String value, boolean truncated) {
    }
}
