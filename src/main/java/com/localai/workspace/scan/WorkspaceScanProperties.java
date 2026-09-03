package com.localai.workspace.scan;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "localrag.scan")
public record WorkspaceScanProperties(
        @NotNull DataSize maxFileSize,
        Set<String> includeExtensions,
        Set<String> includeFileNames,
        Set<String> commonExcludeDirectories,
        Set<String> commonExcludePaths,
        Set<String> unityExcludeDirectories,
        List<String> excludeFilePatterns,
        List<String> sensitiveFilePatterns
) {
}
