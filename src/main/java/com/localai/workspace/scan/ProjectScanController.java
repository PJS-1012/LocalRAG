package com.localai.workspace.scan;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/projects")
public class ProjectScanController {

    private final ProjectFileScanner projectFileScanner;

    public ProjectScanController(ProjectFileScanner projectFileScanner) {
        this.projectFileScanner = projectFileScanner;
    }

    @PostMapping("/{projectName}/scan")
    public ProjectScanResult scan(@PathVariable String projectName) {
        return projectFileScanner.scan(projectName);
    }
}
