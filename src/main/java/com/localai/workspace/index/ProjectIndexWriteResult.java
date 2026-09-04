package com.localai.workspace.index;

public record ProjectIndexWriteResult(
        long writtenCount,
        long deletedCount,
        long storedCount,
        long durationMillis
) {
}
