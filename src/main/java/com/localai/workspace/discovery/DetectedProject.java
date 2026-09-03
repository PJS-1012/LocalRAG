package com.localai.workspace.discovery;

import java.nio.file.Path;
import java.util.List;

public record DetectedProject(
        String name,
        Path rootPath,
        ProjectType projectType,
        String detectedFramework,
        boolean gitRepository,
        boolean enabled,
        List<String> detectionHints
) {
}
