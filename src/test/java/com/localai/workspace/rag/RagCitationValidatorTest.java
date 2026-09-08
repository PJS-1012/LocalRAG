package com.localai.workspace.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagCitationValidatorTest {

    private final RagCitationValidator validator = new RagCitationValidator();
    private final List<RagContextSource> sources = List.of(source("S1"), source("S2"));

    @Test
    void acceptsOneAvailableCitation() {
        CitationValidationResult result = validator.validate("근거입니다 [S1].", sources);

        assertThat(result.usedSourceIds()).containsExactly("S1");
        assertThat(result.invalidSourceIds()).isEmpty();
        assertThat(result.citationMissing()).isFalse();
    }

    @Test
    void extractsMultipleCitationsInFirstUseOrder() {
        CitationValidationResult result = validator.validate("[S2][S1] 그리고 [S2]", sources);

        assertThat(result.usedSourceIds()).containsExactly("S2", "S1");
        assertThat(result.invalidSourceIds()).isEmpty();
    }

    @Test
    void detectsUnavailableCitation() {
        CitationValidationResult result = validator.validate("잘못된 근거 [S99]", sources);

        assertThat(result.usedSourceIds()).containsExactly("S99");
        assertThat(result.invalidSourceIds()).containsExactly("S99");
    }

    @Test
    void flagsMissingCitationOnlyWhenEvidenceExists() {
        assertThat(validator.validate("citation 없음", sources).citationMissing()).isTrue();
        assertThat(validator.validate("근거 없음", List.of()).citationMissing()).isFalse();
    }

    @Test
    void validatesRequestScopedAgentKnowledgeIds() {
        CitationValidationResult result = validator.validateAvailableSourceIds(
                "Evidence [K1-S1], invented [K1-S99].", List.of("K1-S1"));

        assertThat(result.usedSourceIds()).containsExactly("K1-S1", "K1-S99");
        assertThat(result.invalidSourceIds()).containsExactly("K1-S99");
        assertThat(result.citationMissing()).isFalse();
    }

    private RagContextSource source(String id) {
        return new RagContextSource(
                id, 1, "chunk-" + id, "Local_Ai_Work", "src/A.java", "A.java",
                "java", 0, 1, 10, "class A {}", 0.8, "model"
        );
    }
}
