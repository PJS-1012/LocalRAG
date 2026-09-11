package com.localai.workspace.errors;
import jakarta.validation.constraints.*;
import java.util.UUID;
/** No client-supplied evidence, cause, paths, commits, status or model identity is accepted. */
public record ErrorHistorySaveRequest(@NotBlank @Size(max=512) String projectId, @NotNull UUID analysisId) { }
