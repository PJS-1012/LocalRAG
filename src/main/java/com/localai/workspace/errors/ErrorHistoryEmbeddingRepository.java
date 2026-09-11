package com.localai.workspace.errors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ErrorHistoryEmbeddingRepository {
    record StoredIndex(String fingerprint, String model, int dimensions) { }
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;

    public ErrorHistoryEmbeddingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Optional<StoredIndex> storedIndex(long historyId) {
        return jdbc.query("SELECT source_fingerprint, embedding_model, embedding_dimension "
                        + "FROM error_history_embedding WHERE error_history_id = ?",
                (rs, row) -> new StoredIndex(rs.getString(1),rs.getString(2),rs.getInt(3)), historyId)
                .stream().findFirst();
    }

    void upsert(ErrorHistory history, String model, int dimensions, String fingerprint, float[] vector) {
        jdbc.update("""
                INSERT INTO error_history_embedding(error_history_id, project_id, embedding_model,
                    embedding_dimension, source_fingerprint, embedding)
                VALUES (?, ?, ?, ?, ?, CAST(? AS vector))
                ON CONFLICT (error_history_id) DO UPDATE SET
                    project_id = EXCLUDED.project_id,
                    embedding_model = EXCLUDED.embedding_model,
                    embedding_dimension = EXCLUDED.embedding_dimension,
                    source_fingerprint = EXCLUDED.source_fingerprint,
                    embedding = EXCLUDED.embedding,
                    indexed_at = CURRENT_TIMESTAMP
                """, history.id, history.projectId, model, dimensions, fingerprint, vectorLiteral(vector));
    }

    List<ErrorSimilarMatch> search(String projectId, float[] vector, int topK, double threshold) {
        String literal = vectorLiteral(vector);
        return jdbc.query("""
                SELECT h.id, h.status, h.error_type, h.error_message, h.root_cause, h.solution,
                       h.occurred_at, h.related_files, h.related_commits,
                       1 - (e.embedding <=> CAST(? AS vector)) AS similarity
                FROM error_history_embedding e
                JOIN error_history h ON h.id = e.error_history_id
                WHERE e.project_id = ? AND h.project_id = ?
                  AND 1 - (e.embedding <=> CAST(? AS vector)) >= ?
                ORDER BY e.embedding <=> CAST(? AS vector) ASC,
                         CASE h.status WHEN 'RESOLVED' THEN 0 WHEN 'VERIFIED' THEN 1 ELSE 2 END,
                         h.recorded_at DESC
                LIMIT ?
                """, (rs, row) -> new ErrorSimilarMatch(
                        rs.getLong("id"), ErrorStatus.valueOf(rs.getString("status")),
                        rs.getString("error_type"), rs.getString("error_message"),
                        rs.getString("root_cause"), rs.getString("solution"),
                        rs.getTimestamp("occurred_at") == null ? null : rs.getTimestamp("occurred_at").toInstant(),
                        jsonStrings(rs.getString("related_files")), jsonStrings(rs.getString("related_commits")),
                        rs.getDouble("similarity")
                ), literal, projectId, projectId, literal, threshold, literal, topK);
    }

    public long count(String projectId) {
        Long value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM error_history_embedding WHERE project_id = ?", Long.class, projectId);
        return value == null ? 0 : value;
    }

    private List<String> jsonStrings(String json) {
        try {
            return json == null ? List.of() : JSON.readValue(json, new TypeReference<>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String vectorLiteral(float[] vector) {
        return Arrays.stream(toDoubleArray(vector)).mapToObj(Double::toString)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private double[] toDoubleArray(float[] vector) {
        double[] values = new double[vector.length];
        for (int index = 0; index < vector.length; index++) values[index] = vector[index];
        return values;
    }
}
