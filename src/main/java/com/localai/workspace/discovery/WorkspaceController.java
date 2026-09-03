package com.localai.workspace.discovery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final ProjectDiscoveryService projectDiscoveryService;

    public WorkspaceController(ProjectDiscoveryService projectDiscoveryService) {
        this.projectDiscoveryService = projectDiscoveryService;
    }

    @GetMapping("/projects")
    public List<DetectedProject> projects() {
        return projectDiscoveryService.discoverProjects();
    }

    @GetMapping("/discovery")
    public WorkspaceDiscoveryResult discovery() {
        return projectDiscoveryService.discoverWorkspace();
    }
}
