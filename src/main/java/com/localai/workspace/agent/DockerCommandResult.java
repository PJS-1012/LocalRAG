package com.localai.workspace.agent;

record DockerCommandResult(
        boolean started,
        int exitCode,
        String output,
        boolean timedOut,
        boolean truncated
) {
    boolean successful() {
        return started && exitCode == 0 && !timedOut && !truncated;
    }
}
