package com.localai.workspace.workflow;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspaces/projects")
public class DeveloperWorkflowController {
    private final ProjectProgressService progress;
    private final DevelopmentActivityService activity;

    public DeveloperWorkflowController(ProjectProgressService progress, DevelopmentActivityService activity) {
        this.progress=progress; this.activity=activity;
    }

    @PostMapping("/progress/analyze")
    public ProjectProgressResponse progress(@Valid @RequestBody ProjectProgressRequest request) {
        return progress.analyze(request);
    }

    @PostMapping("/activity/summary")
    public DevelopmentActivityResponse activity(@Valid @RequestBody DevelopmentActivityRequest request) {
        return activity.summarize(request);
    }
}
