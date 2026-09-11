package com.localai.workspace.errors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localai.workspace.embedding.EmbeddingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class ErrorSimilarityIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg17").asCompatibleSubstituteFor("postgres"));
    @Autowired ErrorHistoryRepository histories;
    @Autowired ErrorHistoryEmbeddingRepository vectors;
    @Autowired ErrorHistoryEmbeddingService indexer;
    @Autowired ErrorSimilarityService similarities;
    @Autowired ErrorHistoryService historyService;
    @Autowired ObjectMapper json;
    @MockitoBean EmbeddingService embeddings;
    @MockitoBean ErrorProjectScope scope;
    @MockitoBean ErrorAnalysisService analyses;

    @BeforeEach void fixture() {
        histories.deleteAllInBatch();
        when(scope.require(anyString())).thenAnswer(inv->inv.getArgument(0));
        when(embeddings.embedVector(anyString())).thenAnswer(inv->vector(inv.getArgument(0)));
    }

    @Test void retrievesSemanticCandidateOnlyInsideProjectAndKeepsTrustBoundary() {
        ErrorHistory resolved=history("Project-A",ErrorStatus.RESOLVED,"org.postgresql.util.PSQLException",
                "Could not connect to database server","Database requests fail",
                "PostgreSQL was not listening","Started PostgreSQL", "src/Db.java");
        ErrorHistory unverified=history("Project-A",ErrorStatus.UNVERIFIED,"java.lang.NullPointerException",
                "User was null","Request crashed",null,null,"src/User.java");
        ErrorHistory other=history("Project-B",ErrorStatus.RESOLVED,"org.postgresql.util.PSQLException",
                "PostgreSQL connection refused","Other project database failed","other cause","other fix","src/Other.java");
        indexer.index(resolved); indexer.index(unverified); indexer.index(other);

        var result=similarities.find(new ErrorSimilarRequest("Project-A",null,
                "Connection refused while connecting to PostgreSQL","Cannot open database",5));

        assertThat(result.results()).extracting(ErrorSimilarResult::errorHistoryId).containsExactly(resolved.id);
        assertThat(result.results()).noneMatch(item->item.errorHistoryId()==other.id);
        assertThat(result.results().get(0).rootCause()).isEqualTo("PostgreSQL was not listening");
        assertThat(result.results().get(0).solution()).isEqualTo("Started PostgreSQL");
        assertThat(result.results().get(0).trustLabel()).isEqualTo("PAST_RESOLVED_CASE");
        assertThat(result.caution()).contains("does not prove");
        assertThat(Arrays.stream(ErrorSimilarResult.class.getRecordComponents()).map(component->component.getName()))
                .doesNotContain("vector","embedding");
    }

    @Test void unverifiedResultHidesCauseAndFingerprintSkipsThenVerifiedChangeReindexes() {
        ErrorHistory history=history("Project-A",ErrorStatus.UNVERIFIED,"java.lang.NullPointerException",
                "Null user reference","Request failed",null,null,"src/User.java");
        assertThat(indexer.index(history).status()).isEqualTo(ErrorEmbeddingIndexResult.Status.INDEXED);
        assertThat(indexer.index(history).status()).isEqualTo(ErrorEmbeddingIndexResult.Status.UNCHANGED);
        verify(embeddings,times(1)).embedVector(anyString());

        var before=similarities.find(new ErrorSimilarRequest("Project-A","NullPointerException",
                "User reference was null","Request crashed",5));
        assertThat(before.results()).singleElement().satisfies(item->{
            assertThat(item.trustLabel()).isEqualTo("PAST_UNVERIFIED_ANALYSIS");
            assertThat(item.rootCause()).isNull(); assertThat(item.solution()).isNull();
        });

        historyService.changeStatus(history.id,new ErrorHistoryStatusRequest("Project-A",ErrorStatus.VERIFIED,
                "Reproduced", "Missing user validation", null, history.version));
        verify(embeddings,times(3)).embedVector(anyString());
        var after=similarities.find(new ErrorSimilarRequest("Project-A","NullPointerException",
                "User reference was null","Request crashed",5));
        assertThat(after.results()).singleElement().satisfies(item->{
            assertThat(item.status()).isEqualTo(ErrorStatus.VERIFIED);
            assertThat(item.rootCause()).isEqualTo("Missing user validation");
        });
    }

    @Test void currentQueryIsRedactedBeforeEmbedding() {
        ErrorHistory history=history("Project-A",ErrorStatus.UNVERIFIED,"DatabaseError",
                "Connection refused","DB down",null,null,"src/Db.java");
        indexer.index(history);
        clearInvocations(embeddings);
        similarities.find(new ErrorSimilarRequest("Project-A",null,
                "Connection refused password=querySecret","DB down",5));
        var captor=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(embeddings).embedVector(captor.capture());
        assertThat(captor.getValue()).doesNotContain("querySecret").contains("****");
    }

    private ErrorHistory history(String project,ErrorStatus status,String type,String message,String symptom,
            String cause,String solution,String file) {
        ErrorHistory h=new ErrorHistory();
        h.analysisId=UUID.randomUUID(); h.projectId=project; h.occurredAt=Instant.now(); h.recordedAt=Instant.now();
        h.errorType=type; h.errorMessage=message; h.symptom=symptom; h.rootCause=cause; h.solution=solution;
        h.status=status; h.relatedFiles=List.of(file); h.relatedCommits=List.of("abc1234");
        h.evidenceSummary=json.createObjectNode().put("status","fixture"); h.createdBy="TEST";
        if(status!=ErrorStatus.UNVERIFIED) { h.verificationNote="fixture"; h.verifiedBy="TEST"; h.statusChangedAt=Instant.now(); }
        return histories.saveAndFlush(h);
    }

    private float[] vector(String text) {
        float[] value=new float[1024];
        String lower=text.toLowerCase(Locale.ROOT);
        if(lower.contains("postgres") || lower.contains("database") || lower.contains("connection refused")) {
            value[0]=1f;
        } else if(lower.contains("null") || lower.contains("user reference")) {
            value[1]=1f;
        } else { value[2]=1f; }
        return value;
    }
}
