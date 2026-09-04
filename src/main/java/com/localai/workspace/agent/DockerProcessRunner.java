package com.localai.workspace.agent;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
class DockerProcessRunner {

    private final LocalEnvironmentProperties properties;

    DockerProcessRunner(LocalEnvironmentProperties properties) {
        this.properties = properties;
    }

    DockerCommandResult engineVersion() {
        return run(null, List.of("docker", "version", "--format", "{{.Server.Version}}"));
    }

    DockerCommandResult runningContainers() {
        return run(null, List.of("docker", "ps", "--no-trunc", "--format", "{{json .}}"));
    }

    DockerCommandResult projectContainers(Path projectRoot, Path composeFile) {
        return run(projectRoot, List.of(
                "docker", "compose", "-f", composeFile.toString(), "ps", "--all", "--format", "json"
        ));
    }

    private DockerCommandResult run(Path workingDirectory, List<String> command) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            if (workingDirectory != null) {
                builder.directory(workingDirectory.toFile());
            }
            process = builder.start();
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
            return new DockerCommandResult(
                    true, finished ? process.exitValue() : -1, output.value(), !finished, output.truncated()
            );
        } catch (IOException exception) {
            return new DockerCommandResult(false, -1, "", false, false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return new DockerCommandResult(true, -1, "", true, false);
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
