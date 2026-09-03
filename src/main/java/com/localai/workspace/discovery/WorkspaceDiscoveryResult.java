package com.localai.workspace.discovery;

import java.nio.file.Path;
import java.util.List;

public record WorkspaceDiscoveryResult(
        Path workspaceRoot,
        List<DetectedProject> projects,
        List<DetectedContainer> containers
) {
}
