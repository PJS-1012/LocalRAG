package com.localai.workspace.rag;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/projects/rag/context/preview")
public class RagContextController {

    private final RagContextAssemblyService contextAssemblyService;

    public RagContextController(RagContextAssemblyService contextAssemblyService) {
        this.contextAssemblyService = contextAssemblyService;
    }

    @PostMapping
    public RagContextAssemblyResult preview(
            @RequestBody RagContextPreviewRequest request
    ) {
        return contextAssemblyService.assemble(request);
    }
}
