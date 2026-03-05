package com.dsi.rfp.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceRouterTest {

    private final ConfidenceRouter confidenceRouter = new ConfidenceRouter(
        new ConfidenceScoringConfig()
    );

    @Test
    void shouldReturnTrueWhenFieldBelowThreshold() {
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.CONFIDENCE_MAP.value(), Map.of(
            "clientName", 0.5,
            "submissionDeadline", 1.0
        ));

        ExtractionState state = new ExtractionState(data);

        assertThat(confidenceRouter.anyFieldBelowThreshold(state)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenAllAboveThreshold() {
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.CONFIDENCE_MAP.value(), Map.of(
            "clientName", 1.0,
            "submissionDeadline", 0.8
        ));

        ExtractionState state = new ExtractionState(data);

        assertThat(confidenceRouter.anyFieldBelowThreshold(state)).isFalse();
    }

    @Test
    void shouldReturnFalseForEmptyMap() {
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        ExtractionState state = new ExtractionState(data);

        assertThat(confidenceRouter.anyFieldBelowThreshold(state)).isFalse();
    }
}
