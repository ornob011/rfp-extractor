package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.extraction.parser.HeadingNumberParser;
import com.dsi.rfp.adapter.extraction.parser.SectionNumberGrammarValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClauseIdAssignerTest {

    private ClauseIdAssigner assigner;

    @BeforeEach
    void setUp() {
        assigner = new ClauseIdAssigner(
            new HeadingNumberParser(new SectionNumberGrammarValidator())
        );
    }

    @Test
    void shouldProduceDeterministicIdForSameInputs() {
        String id1 = assigner.assignClauseId("CPTU-2025", "2.3 Requirements", 2, 1);
        String id2 = assigner.assignClauseId("CPTU-2025", "2.3 Requirements", 2, 1);
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void shouldNormalizeProcurementRefToLowercase() {
        String id = assigner.assignClauseId("CPTU-2025-ICT-001", "2.3 Requirements", 0, 1);
        assertThat(id).startsWith("cptu-2025-ict-001:");
    }

    @Test
    void shouldReplaceSpacesWithHyphensInRef() {
        String id = assigner.assignClauseId("CPTU 2025 ICT", "1. Scope", 0, 1);
        assertThat(id).startsWith("cptu-2025-ict:");
    }

    @Test
    void shouldStripNonAlphanumericCharsExceptHyphens() {
        String id = assigner.assignClauseId("DSI/GOB/2024-01", "1. Scope", 0, 1);
        assertThat(id).startsWith("dsigob2024-01:");
    }

    @Test
    void shouldTruncateRefToTwentyChars() {
        String longRef = "VERY-LONG-PROCUREMENT-REFERENCE-NUMBER-2025";
        String id = assigner.assignClauseId(longRef, "1. Scope", 0, 1);
        String refPart = id.split(":")[0];
        assertThat(refPart.length()).isLessThanOrEqualTo(20);
    }

    @Test
    void shouldExtractSectionNumberFromTitlePrefix() {
        String id = assigner.assignClauseId("REF", "2.3 Technical Requirements", 0, 1);
        assertThat(id).isEqualTo("ref:2.3:P1");
    }

    @Test
    void shouldUseFallbackIdWhenSectionHasNoNumericPrefix() {
        String id = assigner.assignClauseId("REF", "Scope of Work", 3, 2);
        assertThat(id).isEqualTo("ref:S4:P2");
    }

    @Test
    void shouldUseFallbackIdWhenSectionTitleIsNull() {
        String id = assigner.assignClauseId("REF", null, 0, 1);
        assertThat(id).isEqualTo("ref:S1:P1");
    }

    @Test
    void shouldProduceDifferentIdsForDifferentParagraphIndexes() {
        String id1 = assigner.assignClauseId("REF", "1. Scope", 0, 1);
        String id2 = assigner.assignClauseId("REF", "1. Scope", 0, 2);
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void shouldAssignSubClauseIdWithLetterSuffix() {
        String subId = assigner.assignSubClauseId("ref:1:P1", "a");
        assertThat(subId).isEqualTo("ref:1:P1.a");
    }
}
