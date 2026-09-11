package com.localai.workspace.errors;

import com.localai.workspace.discovery.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class ErrorProjectScope {
    private final ProjectDiscoveryService discovery;
    public ErrorProjectScope(ProjectDiscoveryService discovery) { this.discovery = discovery; }
    public String require(String projectId) {
        return discovery.findProject(discovery.defaultWorkspaceRoot(), projectId)
                .map(DetectedProject::projectId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Project not found in workspace"));
    }
}
