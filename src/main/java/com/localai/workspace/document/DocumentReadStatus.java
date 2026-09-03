package com.localai.workspace.document;

public enum DocumentReadStatus {
    READ_SUCCESS,
    READ_FAILED,
    SKIPPED_EXTENSION,
    SKIPPED_EXCLUDED_PATH,
    SKIPPED_SENSITIVE,
    SKIPPED_TOO_LARGE
}
