package com.localai.workspace.discovery;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "localrag.workspace")
public record WorkspaceProperties(@NotNull Path rootPath) {
}
