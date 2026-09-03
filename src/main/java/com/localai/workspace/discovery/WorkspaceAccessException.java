package com.localai.workspace.discovery;

public class WorkspaceAccessException extends RuntimeException {

    public WorkspaceAccessException(String message) {
        super(message);
    }

    public WorkspaceAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
