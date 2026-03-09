package com.dsi.rfp.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceScoringConfigTest {

    private final ConfidenceScoringConfig config = new ConfidenceScoringConfig();

    @Test
    void shouldLoadLowConfidenceThreshold() {
        assertThat(config.lowConfidenceThreshold()).isEqualTo(0.6);
    }

    @Test
    void shouldLoadManualReviewThreshold() {
        assertThat(config.manualReviewThreshold()).isEqualTo(0.5);
    }

    @Test
    void shouldLoadShortTextLengthThreshold() {
        assertThat(config.shortTextLengthThreshold()).isEqualTo(5);
    }

    @Test
    void shouldLoadBadgeHighThreshold() {
        assertThat(config.badgeHighThreshold()).isEqualTo(0.8);
    }

    @Test
    void shouldLoadBadgeMediumThreshold() {
        assertThat(config.badgeMediumThreshold()).isEqualTo(0.5);
    }

    @Test
    void shouldLoadCompletenessKey() {
        assertThat(config.completenessKey()).isEqualTo("doc_completeness_score");
    }

    @Test
    void shouldLoadAllEntityFields() {
        List<ConfidenceScoringConfig.EntityFieldDef> fields = config.entityFields();

        assertThat(fields).hasSize(49);
    }

    @Test
    void shouldDeriveCorrectCriticalFields() {
        List<String> critical = config.criticalFields();

        assertThat(critical).containsExactlyInAnyOrder(
            "clientName",
            "submissionDeadline",
            "technicalFinancialSplit",
            "markingCriteria",
            "criteria",
            "eligibilitySummary",
            "scopeSummary"
        );
    }

    @Test
    void shouldLoadSectionStrategyConfidence() {
        Map<String, Double> strategies = config.sectionStrategyConfidence();

        assertThat(strategies).containsKeys(
            "TOC", "BOOKMARK", "HEADING_STYLE", "NUMBERED",
            "BANGLA", "FONT_SIZE", "ALL_CAPS", "LLM", "DEFAULT"
        );
        assertThat(strategies.get("TOC")).isEqualTo(1.0);
        assertThat(strategies.get("DEFAULT")).isEqualTo(0.60);
    }

    @Test
    void shouldLoadTableProvenanceConfidence() {
        Map<String, Double> provenance = config.tableProvenanceConfidence();

        assertThat(provenance).containsKeys(
            "LATTICE", "STREAM", "OCR_LLM_RECONSTRUCT", "DEFAULT"
        );
        assertThat(provenance.get("LATTICE")).isEqualTo(0.85);
        assertThat(provenance.get("DEFAULT")).isEqualTo(0.50);
    }

    @Test
    void shouldLoadMaxTotalRepairIterations() {
        assertThat(config.maxTotalRepairIterations()).isEqualTo(20);
    }

    @Test
    void shouldLoadMaxRetriesPerItem() {
        assertThat(config.maxRetriesPerItem()).isEqualTo(3);
    }

    @Test
    void shouldHaveEntityFieldsWithAllRequiredProperties() {
        ConfidenceScoringConfig.EntityFieldDef first = config.entityFields().get(0);

        assertThat(first.key()).isEqualTo("clientName");
        assertThat(first.label()).isEqualTo("Client Name");
        assertThat(first.category()).isEqualTo("general");
        assertThat(first.type()).isEqualTo("text");
        assertThat(first.critical()).isTrue();
    }

    @Test
    void shouldHaveEntityFieldsSpanningAllCategories() {
        List<String> categories = config.entityFields().stream()
                                        .map(ConfidenceScoringConfig.EntityFieldDef::category)
                                        .distinct()
                                        .sorted()
                                        .toList();

        assertThat(categories).containsExactly(
            "evaluation", "financial", "general", "ict",
            "staffing", "submission", "support"
        );
    }
}
