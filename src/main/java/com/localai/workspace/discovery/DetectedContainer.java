package com.localai.workspace.discovery;

import java.nio.file.Path;
import java.util.List;

public record DetectedContainer(
        String name,
        Path rootPath,
        boolean gitRepository,
        List<DetectedProject> projects
) {
}
