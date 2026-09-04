package com.localai.workspace.search;

import com.localai.workspace.chunk.DocumentChunk;
import com.localai.workspace.embedding.EmbeddedChunk;
import com.localai.workspace.index.ProjectIndexRepository;
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

@SpringBootTest
@Testcontainers
@DirtiesContext
class ProjectSemanticSearchRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg17")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private ProjectSemanticSearchRepository searchRepository;

    @Autowired
    private ProjectIndexRepository indexRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.update("DELETE FROM document_chunk_embedding");
    }

    @Test
    void searchesByCosineSimilarityWithinProjectTopKAndThreshold() {
        indexRepository.synchronize("Local_Ai_Work", List.of(
                embedded("Local_Ai_Work", "exact", "Exact.java", vector(1.0f, 0.0f)),
                embedded("Local_Ai_Work", "related", "Related.java", vector(1.0f, 1.0f)),
                embedded("Local_Ai_Work", "irrelevant", "Irrelevant.java", vector(0.0f, 1.0f))
        ));
        indexRepository.synchronize("Other_Project", List.of(
                embedded("Other_Project", "other-exact", "Other.java", vector(1.0f, 0.0f))
        ));

        List<StoredChunkMatch> matches = searchRepository.search(
                "Local_Ai_Work",
                vector(1.0f, 0.0f),
                5,
                0.50
        );

        assertThat(matches).extracting(StoredChunkMatch::chunkId)
                .containsExactly("exact", "related");
        assertThat(matches).allMatch(match -> match.projectId().equals("Local_Ai_Work"));
        assertThat(matches.get(0).similarity()).isCloseTo(1.0, within(0.0001));
        assertThat(matches.get(1).similarity()).isCloseTo(0.7071, within(0.0001));

        assertThat(searchRepository.search(
                "Local_Ai_Work",
                vector(1.0f, 0.0f),
                1,
                0.0
        )).extracting(StoredChunkMatch::chunkId).containsExactly("exact");

        assertThat(searchRepository.search(
                "Local_Ai_Work",
                vector(1.0f, 0.0f),
                5,
                0.80
        )).extracting(StoredChunkMatch::chunkId).containsExactly("exact");
    }

    private EmbeddedChunk embedded(
            String projectId,
            String chunkId,
            String fileName,
            float[] vector
    ) {
        DocumentChunk chunk = new DocumentChunk(
                chunkId,
                projectId,
                fileName,
                fileName,
                "java",
                0,
                "content for " + fileName,
                0,
                10,
                1,
                2,
                "f".repeat(64),
                Instant.parse("2026-09-04T00:00:00Z")
        );
        return new EmbeddedChunk(chunk, "qwen3-embedding:0.6b", 1024, vector);
    }

    private float[] vector(float first, float second) {
        float[] vector = new float[1024];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }

    private org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
