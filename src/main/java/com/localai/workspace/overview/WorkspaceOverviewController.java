package com.localai.workspace.overview;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/workspaces/overview")
public class WorkspaceOverviewController {
    private final WorkspaceOverviewService service;
    public WorkspaceOverviewController(WorkspaceOverviewService service) { this.service=service; }
    @GetMapping public WorkspaceOverviewService.Summary get() { return service.get(); }
}
