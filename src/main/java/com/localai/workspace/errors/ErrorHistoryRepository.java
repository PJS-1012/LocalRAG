package com.localai.workspace.errors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.*;
public interface ErrorHistoryRepository extends JpaRepository<ErrorHistory, Long> {
    Optional<ErrorHistory> findByIdAndProjectId(Long id, String projectId);
    Optional<ErrorHistory> findByAnalysisIdAndProjectId(UUID id, String projectId);
    Page<ErrorHistory> findByProjectId(String projectId, Pageable pageable);
    long countByProjectIdAndStatus(String projectId, ErrorStatus status);
    List<ErrorHistory> findTop5ByProjectIdAndStatusOrderByRecordedAtDesc(String projectId, ErrorStatus status);

    @Query(value="""
            SELECT h.* FROM error_history h
            WHERE h.project_id = :projectId
              AND (CAST(:status AS TEXT) IS NULL OR h.status = CAST(:status AS TEXT))
              AND (CAST(:errorType AS TEXT) IS NULL OR lower(btrim(h.error_type)) = CAST(:errorType AS TEXT))
              AND (CAST(:messagePattern AS TEXT) IS NULL
                   OR lower(h.error_message) LIKE CAST(:messagePattern AS TEXT) ESCAPE '\\')
              AND (CAST(:occurredFrom AS TIMESTAMPTZ) IS NULL
                   OR h.occurred_at >= CAST(:occurredFrom AS TIMESTAMPTZ))
              AND (CAST(:occurredTo AS TIMESTAMPTZ) IS NULL
                   OR h.occurred_at <= CAST(:occurredTo AS TIMESTAMPTZ))
              AND (CAST(:recordedFrom AS TIMESTAMPTZ) IS NULL
                   OR h.recorded_at >= CAST(:recordedFrom AS TIMESTAMPTZ))
              AND (CAST(:recordedTo AS TIMESTAMPTZ) IS NULL
                   OR h.recorded_at <= CAST(:recordedTo AS TIMESTAMPTZ))
              AND (CAST(:relatedFile AS TEXT) IS NULL
                   OR jsonb_exists(h.related_files, CAST(:relatedFile AS TEXT)))
              AND (CAST(:relatedCommit AS TEXT) IS NULL
                   OR jsonb_exists(h.related_commits, CAST(:relatedCommit AS TEXT)))
            ORDER BY h.recorded_at DESC, h.id DESC
            """, countQuery="""
            SELECT count(*) FROM error_history h
            WHERE h.project_id = :projectId
              AND (CAST(:status AS TEXT) IS NULL OR h.status = CAST(:status AS TEXT))
              AND (CAST(:errorType AS TEXT) IS NULL OR lower(btrim(h.error_type)) = CAST(:errorType AS TEXT))
              AND (CAST(:messagePattern AS TEXT) IS NULL
                   OR lower(h.error_message) LIKE CAST(:messagePattern AS TEXT) ESCAPE '\\')
              AND (CAST(:occurredFrom AS TIMESTAMPTZ) IS NULL
                   OR h.occurred_at >= CAST(:occurredFrom AS TIMESTAMPTZ))
              AND (CAST(:occurredTo AS TIMESTAMPTZ) IS NULL
                   OR h.occurred_at <= CAST(:occurredTo AS TIMESTAMPTZ))
              AND (CAST(:recordedFrom AS TIMESTAMPTZ) IS NULL
                   OR h.recorded_at >= CAST(:recordedFrom AS TIMESTAMPTZ))
              AND (CAST(:recordedTo AS TIMESTAMPTZ) IS NULL
                   OR h.recorded_at <= CAST(:recordedTo AS TIMESTAMPTZ))
              AND (CAST(:relatedFile AS TEXT) IS NULL
                   OR jsonb_exists(h.related_files, CAST(:relatedFile AS TEXT)))
              AND (CAST(:relatedCommit AS TEXT) IS NULL
                   OR jsonb_exists(h.related_commits, CAST(:relatedCommit AS TEXT)))
            """, nativeQuery=true)
    Page<ErrorHistory> search(@Param("projectId") String projectId,
            @Param("status") String status, @Param("errorType") String errorType,
            @Param("messagePattern") String messagePattern,
            @Param("occurredFrom") Instant occurredFrom, @Param("occurredTo") Instant occurredTo,
            @Param("recordedFrom") Instant recordedFrom, @Param("recordedTo") Instant recordedTo,
            @Param("relatedFile") String relatedFile, @Param("relatedCommit") String relatedCommit,
            Pageable pageable);
}
