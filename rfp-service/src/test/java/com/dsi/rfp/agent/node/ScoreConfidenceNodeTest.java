package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RfpEntities;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreConfidenceNodeTest {

    private final ScoreConfidenceNode node = new ScoreConfidenceNode(
        new com.dsi.rfp.agent.ConfidenceScoringConfig()
    );

    @Test
    void shouldReturnZeroCompletenessWhenEntitiesNull() {
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        ExtractionState state = new ExtractionState(data);

        Map<String, Object> result = node.apply(state);

        Map<String, Double> confidenceMap = readConfidenceMap(result);
        assertThat(confidenceMap.get("doc_completeness_score")).isEqualTo(0.0);
    }

    @Test
    void shouldScoreNullFieldsAsZero() {
        RfpEntities entities = RfpEntities.builder().build();
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.ENTITIES.value(), entities);
        ExtractionState state = new ExtractionState(data);

        Map<String, Object> result = node.apply(state);

        Map<String, Double> confidenceMap = readConfidenceMap(result);
        assertThat(confidenceMap.get("clientName")).isEqualTo(0.0);
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
        ExtractionState state = new ExtractionState(data);

        Map<String, Object> result = node.apply(state);

        Map<String, Double> confidenceMap = readConfidenceMap(result);
        assertThat(confidenceMap.get("clientName")).isEqualTo(1.0);
        assertThat(confidenceMap.get("doc_completeness_score")).isEqualTo(1.0);
    }

    @Test
    void shouldAddLowConfidenceFieldsToQueue() {
        RfpEntities entities = RfpEntities.builder()
                                          .clientName("AB")
                                          .build();

        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.ENTITIES.value(), entities);
        ExtractionState state = new ExtractionState(data);

        Map<String, Object> result = node.apply(state);

        List<String> lowConfQueue = readLowConfidenceQueue(result);
        assertThat(lowConfQueue).contains("clientName");
    }

    private Map<String, Double> readConfidenceMap(Map<String, Object> result) {
        Map<?, ?> map = (Map<?, ?>) result.get(ExtractionState.Key.CONFIDENCE_MAP.value());

        return map.entrySet()
                  .stream()
                  .filter(entry -> entry.getKey() instanceof String)
                  .filter(entry -> entry.getValue() instanceof Number)
                  .collect(java.util.stream.Collectors.toMap(
                      entry -> (String) entry.getKey(),
                      entry -> ((Number) entry.getValue()).doubleValue()
                  ));
    }

    private List<String> readLowConfidenceQueue(Map<String, Object> result) {
        List<?> values = (List<?>) result.get(ExtractionState.Key.LOW_CONFIDENCE_QUEUE.value());

        return values.stream()
                     .filter(String.class::isInstance)
                     .map(String.class::cast)
                     .toList();
    }
}
