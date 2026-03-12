package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.table.TableExtractionConfig;
import com.dsi.rfp.agent.ConfidenceScorer;
import com.dsi.rfp.agent.ConfidenceScoringConfig;
import com.dsi.rfp.agent.EntityFieldReader;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreConfidenceNodeTest {

    private final ScoreConfidenceNode node = createNode();

    @Test
    void shouldReturnZeroCompletenessWhenEntitiesNull() {
        ExtractionState state = new ExtractionState(ExtractionState.initial(1L, "/tmp/x.pdf"));

        Map<String, Object> result = node.apply(state);

        assertThat(readConfidenceMap(result).get("doc_completeness_score")).isEqualTo(0.0);
    }

    @Test
    void shouldScoreNullFieldsAsZero() {
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.ENTITIES.value(), RfpEntities.builder().build());

        Map<String, Object> result = node.apply(new ExtractionState(data));

        assertThat(readConfidenceMap(result).get("clientName")).isEqualTo(0.0);
    }

    @Test
    void shouldScoreLongFieldsAsOne() {
        RfpEntities entities = RfpEntities.builder()
                                          .clientName("Government of Bangladesh")
                                          .submissionDeadline("2025-06-30")
                                          .technicalFinancialSplit("80/20")
                                          .markingCriteria("Technical: 80%, Financial: 20%")
                                          .criteria("Multiple criteria defined")
                                          .eligibilitySummary("Must have 5 years experience")
                                          .scopeSummary("Full system implementation required")
                                          .build();
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.ENTITIES.value(), entities);

        Map<String, Object> result = node.apply(new ExtractionState(data));

        Map<String, Double> confidenceMap = readConfidenceMap(result);
        assertThat(confidenceMap.get("clientName")).isEqualTo(1.0);
        assertThat(confidenceMap.get("doc_completeness_score")).isEqualTo(1.0);
    }

    @Test
    void shouldAddNullFieldsToLowConfidenceQueue() {
        RfpEntities entities = RfpEntities.builder()
                                          .submissionDeadline("2025-06-30")
                                          .build();
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.ENTITIES.value(), entities);

        Map<String, Object> result = node.apply(new ExtractionState(data));

        assertThat(readLowConfidenceQueue(result)).contains("clientName");
    }

    @Test
    void shouldUseBookmarkConfidenceForSection() {
        UUID sectionId = UUID.randomUUID();
        Section section = Section.builder()
                                 .id(sectionId)
                                 .title("Introduction")
                                 .level(1)
                                 .pageStart(1)
                                 .pageEnd(5)
                                 .confidence(SectionConfidence.builder()
                                                              .score(0.95)
                                                              .method(HeadingDetectionMethod.BOOKMARK)
                                                              .build())
                                 .build();
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.SECTIONS.value(), List.of(section));

        Map<String, Object> result = node.apply(new ExtractionState(data));

        assertThat(readConfidenceMap(result).get(sectionId.toString())).isEqualTo(0.95);
    }

    @Test
    void shouldEnqueueLowConfidenceSectionToQueue() {
        UUID sectionId = UUID.randomUUID();
        Section section = Section.builder()
                                 .id(sectionId)
                                 .title("Misc")
                                 .level(1)
                                 .pageStart(1)
                                 .pageEnd(2)
                                 .confidence(SectionConfidence.builder()
                                                              .score(0.55)
                                                              .method(HeadingDetectionMethod.ALL_CAPS)
                                                              .build())
                                 .build();
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.SECTIONS.value(), List.of(section));

        Map<String, Object> result = node.apply(new ExtractionState(data));

        assertThat(readLowConfidenceQueue(result)).contains(sectionId.toString());
    }

    @Test
    void shouldUseStreamConfidenceForTable() {
        UUID tableId = UUID.randomUUID();
        TableExtractionResult table = TableExtractionResult.builder()
                                                           .tableId(tableId)
                                                           .pageStart(1)
                                                           .pageEnd(1)
                                                           .confidence(ExtractionConfidence.builder()
                                                                                           .score(0.65)
                                                                                           .method("stream")
                                                                                           .build())
                                                           .build();
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.TABLES.value(), List.of(table));

        Map<String, Object> result = node.apply(new ExtractionState(data));

        assertThat(readConfidenceMap(result).get(tableId.toString())).isEqualTo(0.65);
    }

    @Test
    void shouldPopulateRepairableComponentsForNullFields() {
        RfpEntities entities = RfpEntities.builder()
                                          .submissionDeadline("2025-06-30")
                                          .build();
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.ENTITIES.value(), entities);

        Map<String, Object> result = node.apply(new ExtractionState(data));
        Map<String, RepairableComponent> repairables = readRepairables(result);

        assertThat(repairables).containsKey("clientName");
        assertThat(repairables.get("clientName").getComponentType())
            .isEqualTo(RepairComponentType.ENTITY);
    }

    @Test
    void shouldEnqueueNullCriticalEntityToManualReview() {
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.ENTITIES.value(), RfpEntities.builder().build());

        Map<String, Object> result = node.apply(new ExtractionState(data));

        assertThat(readStringList(result, ExtractionState.Key.MANUAL_REVIEW_REQUIRED)).isNotEmpty();
    }

    @Test
    void shouldComputeDocCompletenessScoreCorrectly() {
        RfpEntities entities = RfpEntities.builder()
                                          .clientName("Government of Bangladesh")
                                          .submissionDeadline("2025-06-30")
                                          .technicalFinancialSplit("80/20")
                                          .build();
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.ENTITIES.value(), entities);

        Map<String, Object> result = node.apply(new ExtractionState(data));

        double completeness = readConfidenceMap(result).get("doc_completeness_score");
        assertThat(completeness).isGreaterThan(0.0);
        assertThat(completeness).isLessThanOrEqualTo(1.0);
    }

    private ScoreConfidenceNode createNode() {
        ConfidenceScoringConfig config = new ConfidenceScoringConfig();
        EntityFieldReader fieldReader = new EntityFieldReader(new ObjectMapper());
        ConfidenceScorer scorer = new ConfidenceScorer(
            config,
            fieldReader,
            new TableExtractionConfig()
        );

        return new ScoreConfidenceNode(
            config,
            scorer,
            fieldReader
        );
    }

    private Map<String, Double> readConfidenceMap(Map<String, Object> result) {
        return (Map<String, Double>) result.get(ExtractionState.Key.CONFIDENCE_MAP.value());
    }

    private List<String> readLowConfidenceQueue(Map<String, Object> result) {
        return readStringList(result, ExtractionState.Key.LOW_CONFIDENCE_QUEUE);
    }

    private List<String> readStringList(
        Map<String, Object> result,
        ExtractionState.Key key
    ) {
        return (List<String>) result.get(key.value());
    }

    private Map<String, RepairableComponent> readRepairables(Map<String, Object> result) {
        return (Map<String, RepairableComponent>) result.get(
            ExtractionState.Key.REPAIRABLE_COMPONENTS.value()
        );
    }
}
