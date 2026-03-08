package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.exception.RulePackEvaluationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JmesPathEvaluatorTest {

    private final JmesPathEvaluator evaluator = new JmesPathEvaluator(new ObjectMapper());

    @Test
    void shouldReturnTrueWhenFieldPresentAndConditionMatches() {
        String json = "{\"entities\":{\"clientName\":\"ICTD\"}}";

        boolean result = evaluator.evaluateAsBoolean(
            "entities.clientName != null",
            json
        );

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenFieldIsNull() {
        String json = "{\"entities\":{\"clientName\":null}}";

        boolean result = evaluator.evaluateAsBoolean(
            "entities.clientName != null",
            json
        );

        assertThat(result).isFalse();
    }

    @Test
    void shouldReturnFalseWhenFieldIsMissing() {
        String json = "{\"entities\":{}}";

        boolean result = evaluator.evaluateAsBoolean(
            "entities.clientName != null",
            json
        );

        assertThat(result).isFalse();
    }

    @Test
    void shouldThrowWhenExpressionIsInvalid() {
        String json = "{\"entities\":{}}";

        assertThatThrownBy(() -> evaluator.evaluateAsBoolean(
            "entities[invalid syntax",
            json
        )).isInstanceOf(RulePackEvaluationException.class);
    }

    @Test
    void shouldExtractEvidenceStringWhenPathExists() {
        String json = "{\"entities\":{\"clientName\":\"ICTD\"}}";

        Optional<String> evidence = evaluator.extractEvidence(
            "entities.clientName",
            json
        );

        assertThat(evidence).isPresent().contains("ICTD");
    }

    @Test
    void shouldReturnEmptyWhenEvidencePathIsNull() {
        String json = "{\"entities\":{\"clientName\":null}}";

        Optional<String> evidence = evaluator.extractEvidence(
            "entities.clientName",
            json
        );

        assertThat(evidence).isEmpty();
    }

    @Test
    void shouldReturnTrueForBooleanConditionWithAndOperator() {
        String json = "{\"entities\":{\"submissionDeadline\":\"2026-01-01\",\"bidValidityPeriod\":\"90 days\"}}";

        boolean result = evaluator.evaluateAsBoolean(
            "entities.submissionDeadline != null && entities.bidValidityPeriod != null",
            json
        );

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnTrueForArrayLengthCheck() {
        String json = "{\"sections\":[{\"title\":\"Intro\"},{\"title\":\"Scope\"}]}";

        boolean result = evaluator.evaluateAsBoolean(
            "sections[?title] | length(@) > `0`",
            json
        );

        assertThat(result).isTrue();
    }
}
