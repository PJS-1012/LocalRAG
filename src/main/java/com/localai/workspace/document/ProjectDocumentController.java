package com.localai.workspace.document;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/projects")
public class ProjectDocumentController {

    private final ProjectDocumentReader documentReader;

    public ProjectDocumentController(ProjectDocumentReader documentReader) {
        this.documentReader = documentReader;
    }

    @PostMapping("/documents/read")
    public DocumentReadResult readById(
            @RequestParam String projectId,
            @RequestParam String filePath
    ) {
        return documentReader.read(projectId, filePath);
    }

    @PostMapping("/{projectName}/documents/read")
    public DocumentReadResult read(
            @PathVariable String projectName,
            @RequestParam String filePath
    ) {
        return documentReader.read(projectName, filePath);
    }
}
