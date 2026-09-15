package com.localai.workspace.overview;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspaces/projects/overview")
public class ProjectOverviewController {
    private final ProjectOverviewService service;
    public ProjectOverviewController(ProjectOverviewService service){this.service=service;}

    @GetMapping
    public ProjectOverview get(@RequestParam String projectId){return service.get(projectId);}
}
