package com.localai.workspace.errors;
import jakarta.validation.constraints.*;
public record ErrorHistoryStatusRequest(@NotBlank @Size(max=512) String projectId,
        @NotNull ErrorStatus status, @NotBlank @Size(max=4000) String verificationNote,
        @Size(max=4000) String rootCause, @Size(max=4000) String solution,
        @NotNull @PositiveOrZero Long expectedVersion) { }
