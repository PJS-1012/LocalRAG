package com.localai.workspace.errors;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.Instant;

@RestController
@RequestMapping("/api/workspaces/projects/errors")
public class ErrorController {
    private final ErrorAnalysisService analyses;
    private final ErrorHistoryService histories;
    private final ErrorSimilarityService similarities;
    public ErrorController(ErrorAnalysisService analyses, ErrorHistoryService histories,
            ErrorSimilarityService similarities) {
        this.analyses=analyses; this.histories=histories; this.similarities=similarities;
    }
    @PostMapping("/analyze")
    public ErrorAnalysisResult analyze(@Valid @RequestBody ErrorAnalysisRequest request) { return analyses.analyzeError(request); }
    @PostMapping("/history")
    public ErrorHistoryService.SaveResult save(@Valid @RequestBody ErrorHistorySaveRequest request) {
        return histories.saveErrorHistory(request);
    }
    @PostMapping("/similar")
    public ErrorSimilarityResponse similar(@Valid @RequestBody ErrorSimilarRequest request) {
        return similarities.find(request);
    }
    @GetMapping("/history")
    public ErrorHistoryService.HistoryPage list(@RequestParam String projectId,
            @RequestParam(required=false) ErrorStatus status,
            @RequestParam(required=false) String errorType,
            @RequestParam(required=false) String errorMessage,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant occurredFrom,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant occurredTo,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant recordedFrom,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant recordedTo,
            @RequestParam(required=false) String relatedFile,
            @RequestParam(required=false) String relatedCommit,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return histories.list(new ErrorHistoryFilter(projectId,status,errorType,errorMessage,
                occurredFrom,occurredTo,recordedFrom,recordedTo,relatedFile,relatedCommit,page,size));
    }
    @GetMapping("/history/{id}")
    public ErrorHistoryDetail detail(@PathVariable long id,@RequestParam String projectId) {
        return histories.detail(id,projectId);
    }
    @PatchMapping("/history/{id}/status")
    public ErrorHistoryView status(@PathVariable long id,@Valid @RequestBody ErrorHistoryStatusRequest request) {
        return histories.changeStatus(id,request);
    }
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<String> conflict() { return ResponseEntity.status(409).body("Concurrent history update; retry request"); }
}
