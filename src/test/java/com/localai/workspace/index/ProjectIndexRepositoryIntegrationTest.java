package com.localai.workspace.index;

import com.localai.workspace.chunk.DocumentChunk;
import com.localai.workspace.embedding.EmbeddedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@DirtiesContext
class ProjectIndexRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg17")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private ProjectIndexRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.update("DELETE FROM document_chunk_embedding");
    }

    @Test
    void synchronizesNewUnchangedChangedAndDeletedChunksWithoutCrossingProjects() {
        EmbeddedChunk first = embedded("Local_Ai_Work", "chunk-1", "README.md", 0, "first", 1024);
        EmbeddedChunk second = embedded("Local_Ai_Work", "chunk-2", "README.md", 1, "second", 1024);
        EmbeddedChunk otherProject = embedded("Other_Project", "other-1", "README.md", 0, "other", 1024);

        ProjectIndexWriteResult initial = repository.synchronize(
                "Local_Ai_Work",
                List.of(first, second)
        );
        repository.synchronize("Other_Project", List.of(otherProject));

        assertThat(initial.writtenCount()).isEqualTo(2);
        assertThat(initial.deletedCount()).isZero();
        assertThat(initial.storedCount()).isEqualTo(2);
        assertThat(repository.count("Other_Project")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT vector_dims(embedding) FROM document_chunk_embedding WHERE chunk_id = ?",
                Integer.class,
                "chunk-1"
        )).isEqualTo(1024);

        ProjectIndexWriteResult unchanged = repository.synchronize(
                "Local_Ai_Work",
                List.of(first, second)
        );

        assertThat(unchanged.writtenCount()).isZero();
        assertThat(unchanged.deletedCount()).isZero();
        assertThat(unchanged.storedCount()).isEqualTo(2);

        EmbeddedChunk changed = embedded(
                "Local_Ai_Work",
                "chunk-2-changed",
                "README.md",
                1,
                "changed second",
                1024
        );
        ProjectIndexWriteResult changedResult = repository.synchronize(
                "Local_Ai_Work",
                List.of(first, changed)
        );

        assertThat(changedResult.writtenCount()).isEqualTo(1);
        assertThat(changedResult.deletedCount()).isEqualTo(1);
        assertThat(changedResult.storedCount()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunk_embedding WHERE chunk_id = 'chunk-2'",
                Long.class
        )).isZero();

        ProjectIndexWriteResult deletedResult = repository.synchronize(
                "Local_Ai_Work",
                List.of(changed)
        );

        assertThat(deletedResult.writtenCount()).isZero();
        assertThat(deletedResult.deletedCount()).isEqualTo(1);
        assertThat(deletedResult.storedCount()).isEqualTo(1);
        assertThat(repository.count("Other_Project")).isEqualTo(1);
        assertThat(repository.stats("Local_Ai_Work")).hasValueSatisfying(stats -> {
            assertThat(stats.storedChunkCount()).isEqualTo(1);
            assertThat(stats.embeddingModel()).isEqualTo("qwen3-embedding:0.6b");
            assertThat(stats.dimensions()).isEqualTo(1024);
            assertThat(stats.latestIndexedAt()).isNotNull();
        });
    }

    @Test
    void rollsBackTheWholeProjectWriteWhenOneVectorIsInvalid() {
        EmbeddedChunk valid = embedded(
                "rollback-project",
                "rollback-valid",
                "README.md",
                0,
                "valid",
                1024
        );
        EmbeddedChunk invalid = embedded(
                "rollback-project",
                "rollback-invalid",
                "README.md",
                1,
                "invalid",
                3
        );

        assertThatThrownBy(() -> repository.synchronize(
                "rollback-project",
                List.of(valid, invalid)
        )).isInstanceOf(RuntimeException.class);

        assertThat(repository.count("rollback-project")).isZero();
    }

    private EmbeddedChunk embedded(
            String projectId,
            String chunkId,
            String path,
            int index,
            String content,
            int dimensions
    ) {
        float[] vector = new float[dimensions];
        for (int vectorIndex = 0; vectorIndex < vector.length; vectorIndex++) {
            vector[vectorIndex] = vectorIndex / 10_000.0f;
        }
        DocumentChunk chunk = new DocumentChunk(
                chunkId,
                projectId,
                path,
                "README.md",
                "md",
                index,
                content,
                index * 100,
                index * 100 + content.length(),
                1,
                1,
                "f".repeat(64),
                Instant.parse("2026-09-04T00:00:00Z")
        );
        return new EmbeddedChunk(
                chunk,
                "qwen3-embedding:0.6b",
                dimensions,
                vector
        );
    }
}
