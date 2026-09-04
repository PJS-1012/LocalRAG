package com.localai.workspace.index;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/projects/index")
public class ProjectIndexController {

    private final ProjectIndexService indexService;

    public ProjectIndexController(ProjectIndexService indexService) {
        this.indexService = indexService;
    }

    @PostMapping
    public ProjectIndexResult index(@RequestParam String projectId) {
        return indexService.index(projectId);
    }

    @GetMapping("/stats")
    public ProjectIndexStats stats(@RequestParam String projectId) {
        return indexService.stats(projectId);
    }
}
