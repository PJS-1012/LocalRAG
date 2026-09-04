package com.localai.workspace.agent;

record GitCommandResult(
        int exitCode,
        String output,
        boolean timedOut,
        boolean truncated
) {
    boolean successful() {
        return exitCode == 0 && !timedOut && !truncated;
    }
}
