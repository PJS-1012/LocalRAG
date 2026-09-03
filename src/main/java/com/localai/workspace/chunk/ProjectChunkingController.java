package com.localai.workspace.chunk;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/projects/chunks")
public class ProjectChunkingController {

    private final ProjectChunkingService chunkingService;

    public ProjectChunkingController(ProjectChunkingService chunkingService) {
        this.chunkingService = chunkingService;
    }

    @PostMapping("/preview")
    public ProjectChunkingResult preview(@RequestParam String projectId) {
        return chunkingService.chunk(projectId);
    }
}
