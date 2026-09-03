package com.localai.workspace.scan;

public enum FileScanStatus {
    SUPPORTED,
    SKIPPED_EXTENSION,
    SKIPPED_EXCLUDED_PATH,
    SKIPPED_SENSITIVE,
    SKIPPED_TOO_LARGE,
    METADATA_FAILED
}
