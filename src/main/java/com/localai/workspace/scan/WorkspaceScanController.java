package com.localai.workspace.scan;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceScanController {

    private final WorkspaceMetadataScanService metadataScanService;

    public WorkspaceScanController(WorkspaceMetadataScanService metadataScanService) {
        this.metadataScanService = metadataScanService;
    }

    @PostMapping("/scan")
    public WorkspaceScanSummary scan() {
        return metadataScanService.scan();
    }
}
