package com.localai.workspace.search;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/projects/search")
public class ProjectSemanticSearchController {

    private final ProjectSemanticSearchService searchService;

    public ProjectSemanticSearchController(ProjectSemanticSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping
    public ProjectSemanticSearchResponse search(
            @RequestBody ProjectSemanticSearchRequest request
    ) {
        return searchService.search(request);
    }
}
