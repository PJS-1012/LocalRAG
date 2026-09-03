package com.localai.workspace.document;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/projects")
public class ProjectDocumentBatchController {

    private final ProjectDocumentBatchReader batchReader;

    public ProjectDocumentBatchController(ProjectDocumentBatchReader batchReader) {
        this.batchReader = batchReader;
    }

    @PostMapping("/{projectName}/documents/read-all")
    public ProjectDocumentReadResult readAll(@PathVariable String projectName) {
        return batchReader.read(projectName);
    }
}
