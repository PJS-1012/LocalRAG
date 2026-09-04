package com.localai.workspace.search;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class ProjectSemanticSearchRepository {

    private static final String SEARCH_SQL = """
            SELECT chunk_id,
                   project_id,
                   source_path,
                   source_file_name,
                   extension,
                   chunk_index,
                   start_line,
                   end_line,
                   content,
                   1 - cosine_distance AS similarity,
                   embedding_model
            FROM (
                SELECT chunk_id,
                       project_id,
                       source_path,
                       source_file_name,
                       extension,
                       chunk_index,
                       start_line,
                       end_line,
                       content,
                       embedding_model,
                       embedding <=> CAST(? AS vector) AS cosine_distance
                FROM document_chunk_embedding
                WHERE project_id = ?
            ) ranked
            WHERE 1 - cosine_distance >= ?
            ORDER BY cosine_distance ASC, chunk_id ASC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProjectSemanticSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long count(String projectId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunk_embedding WHERE project_id = ?",
                Long.class,
                projectId
        );
        return count == null ? 0 : count;
    }

    public List<StoredChunkMatch> search(
            String projectId,
            float[] queryVector,
            int topK,
            double threshold
    ) {
        return jdbcTemplate.query(
                SEARCH_SQL,
                (resultSet, rowNumber) -> new StoredChunkMatch(
                        resultSet.getString("chunk_id"),
                        resultSet.getString("project_id"),
                        resultSet.getString("source_path"),
                        resultSet.getString("source_file_name"),
                        resultSet.getString("extension"),
                        resultSet.getInt("chunk_index"),
                        resultSet.getInt("start_line"),
                        resultSet.getInt("end_line"),
                        resultSet.getString("content"),
                        resultSet.getDouble("similarity"),
                        resultSet.getString("embedding_model")
                ),
                vectorLiteral(queryVector),
                projectId,
                threshold,
                topK
        );
    }

    private String vectorLiteral(float[] vector) {
        return Arrays.stream(toDoubleArray(vector))
                .mapToObj(Double::toString)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private double[] toDoubleArray(float[] vector) {
        double[] values = new double[vector.length];
        for (int index = 0; index < vector.length; index++) {
            values[index] = vector[index];
        }
        return values;
    }
}
