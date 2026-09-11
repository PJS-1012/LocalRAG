package com.localai.workspace.errors;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
public record ErrorHistoryView(Long id, UUID analysisId, String projectId, Instant occurredAt, Instant recordedAt,
        String errorType, String errorMessage, String symptom, String rootCause, String solution, ErrorStatus status,
        List<String> relatedFiles, List<String> relatedCommits, JsonNode evidenceSummary,
        String createdBy, String verificationNote, String verifiedBy, Instant statusChangedAt, long version) {
    static ErrorHistoryView from(ErrorHistory h) {
        return new ErrorHistoryView(h.id,h.analysisId,h.projectId,h.occurredAt,h.recordedAt,h.errorType,h.errorMessage,
                h.symptom,h.rootCause,h.solution,h.status,List.copyOf(h.relatedFiles),List.copyOf(h.relatedCommits),
                h.evidenceSummary.deepCopy(),h.createdBy,h.verificationNote,h.verifiedBy,h.statusChangedAt,h.version);
    }
}
