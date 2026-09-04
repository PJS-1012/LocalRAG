package com.localai.workspace.index;

import com.localai.workspace.chunk.DocumentChunk;
import com.localai.workspace.embedding.EmbeddedChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ProjectIndexRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO document_chunk_embedding (
                chunk_id,
                project_id,
                source_path,
                source_file_name,
                extension,
                chunk_index,
                content,
                start_offset,
                end_offset,
                start_line,
                end_line,
                source_fingerprint,
                source_modified_at,
                embedding_model,
                embedding_dimension,
                embedding
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
            ON CONFLICT (chunk_id) DO UPDATE SET
                source_modified_at = EXCLUDED.source_modified_at,
                embedding_model = EXCLUDED.embedding_model,
                embedding_dimension = EXCLUDED.embedding_dimension,
                embedding = EXCLUDED.embedding,
                indexed_at = CURRENT_TIMESTAMP
            WHERE document_chunk_embedding.source_modified_at IS DISTINCT FROM EXCLUDED.source_modified_at
               OR document_chunk_embedding.embedding_model IS DISTINCT FROM EXCLUDED.embedding_model
               OR document_chunk_embedding.embedding_dimension IS DISTINCT FROM EXCLUDED.embedding_dimension
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProjectIndexRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ProjectIndexWriteResult synchronize(String projectId, List<EmbeddedChunk> chunks) {
        Objects.requireNonNull(projectId, "projectId");
        List<EmbeddedChunk> safeChunks = List.copyOf(chunks);
        if (safeChunks.stream().anyMatch(chunk -> !projectId.equals(chunk.chunk().projectId()))) {
            throw new IllegalArgumentException("Every Chunk must belong to the selected Project");
        }

        long startedAt = System.nanoTime();
        long writtenCount = 0;
        for (EmbeddedChunk embeddedChunk : safeChunks) {
            writtenCount += upsert(embeddedChunk);
        }

        List<String> currentChunkIds = safeChunks.stream()
                .map(embedded -> embedded.chunk().chunkId())
                .toList();
        long deletedCount = deleteStale(projectId, currentChunkIds);
        long storedCount = count(projectId);

        return new ProjectIndexWriteResult(
                writtenCount,
                deletedCount,
                storedCount,
                (System.nanoTime() - startedAt) / 1_000_000
        );
    }

    public long count(String projectId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunk_embedding WHERE project_id = ?",
                Long.class,
                projectId
        );
        return count == null ? 0 : count;
    }

    public Optional<ProjectIndexStats> stats(String projectId) {
        return jdbcTemplate.query("""
                        SELECT project_id,
                               COUNT(*) AS stored_chunk_count,
                               MAX(embedding_model) AS embedding_model,
                               MAX(embedding_dimension) AS dimensions,
                               MAX(indexed_at) AS latest_indexed_at
                        FROM document_chunk_embedding
                        WHERE project_id = ?
                        GROUP BY project_id
                        """,
                (resultSet, rowNumber) -> new ProjectIndexStats(
                        resultSet.getString("project_id"),
                        resultSet.getLong("stored_chunk_count"),
                        resultSet.getString("embedding_model"),
                        resultSet.getInt("dimensions"),
                        resultSet.getTimestamp("latest_indexed_at").toInstant()
                ),
                projectId
        ).stream().findFirst();
    }

    private int upsert(EmbeddedChunk embeddedChunk) {
        DocumentChunk chunk = embeddedChunk.chunk();
        return jdbcTemplate.update(UPSERT_SQL, preparedStatement -> {
            preparedStatement.setString(1, chunk.chunkId());
            preparedStatement.setString(2, chunk.projectId());
            preparedStatement.setString(3, chunk.sourceFilePath());
            preparedStatement.setString(4, chunk.sourceFileName());
            preparedStatement.setString(5, chunk.extension());
            preparedStatement.setInt(6, chunk.chunkIndex());
            preparedStatement.setString(7, chunk.content());
            preparedStatement.setInt(8, chunk.startOffset());
            preparedStatement.setInt(9, chunk.endOffset());
            preparedStatement.setInt(10, chunk.startLine());
            preparedStatement.setInt(11, chunk.endLine());
            preparedStatement.setString(12, chunk.sourceFingerprint());
            preparedStatement.setTimestamp(13, Timestamp.from(chunk.sourceModifiedAt()));
            preparedStatement.setString(14, embeddedChunk.embeddingModel());
            preparedStatement.setInt(15, embeddedChunk.dimensions());
            preparedStatement.setString(16, vectorLiteral(embeddedChunk.vector()));
        });
    }

    private int deleteStale(String projectId, List<String> currentChunkIds) {
        if (currentChunkIds.isEmpty()) {
            return jdbcTemplate.update(
                    "DELETE FROM document_chunk_embedding WHERE project_id = ?",
                    projectId
            );
        }

        String placeholders = String.join(", ", Collections.nCopies(currentChunkIds.size(), "?"));
        String sql = "DELETE FROM document_chunk_embedding "
                + "WHERE project_id = ? AND chunk_id NOT IN (" + placeholders + ")";
        List<Object> arguments = new ArrayList<>(currentChunkIds.size() + 1);
        arguments.add(projectId);
        arguments.addAll(currentChunkIds);
        return jdbcTemplate.update(sql, arguments.toArray());
    }

    private String vectorLiteral(float[] vector) {
        return java.util.Arrays.stream(toDoubleArray(vector))
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
