package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClarificationQuestionTriggerTest {

    private ClarificationQuestionTrigger trigger;

    @BeforeEach
    void setUp() {
        trigger = new ClarificationQuestionTrigger(
            new ArtifactGenerationConfig()
        );
    }

    @Test
    void shouldProduceMandatoryTriggerForFatalFailFinding() {
        RulePackResults results = resultsWithFindings(List.of(
            finding("BD-ICT-001", RuleSeverity.FATAL, RuleStatus.FAIL)
        ));
        ExtractionState state = stateWithConfidence(Map.of());
        RfpDocument doc = minimalDoc();

        List<ClarificationTrigger> triggers = trigger.detect(
            doc,
            results,
            state
        );

        assertThat(triggers).hasSize(1);
        assertThat(triggers.getFirst().getType())
            .isEqualTo(QuestionType.MANDATORY_CLARIFICATION);
        assertThat(triggers.getFirst().getRuleId())
            .isEqualTo("BD-ICT-001");
    }

    @Test
    void shouldProduceConfirmationTriggerForLowConfidenceEntity() {
        RulePackResults results = resultsWithFindings(List.of());
        ExtractionState state = stateWithConfidence(
            Map.of("submissionDeadline", 0.3)
        );
        RfpDocument doc = minimalDoc();

        List<ClarificationTrigger> triggers = trigger.detect(
            doc,
            results,
            state
        );

        assertThat(triggers).hasSize(1);
        assertThat(triggers.getFirst().getType())
            .isEqualTo(QuestionType.CONFIRMATION);
        assertThat(triggers.getFirst().getEntityField())
            .isEqualTo("submissionDeadline");
    }

    @Test
    void shouldProduceContradictionTriggerWhenIssueDateAfterDeadline() {
        RulePackResults results = resultsWithFindings(List.of());
        ExtractionState state = stateWithConfidence(Map.of());

        RfpDocument doc = RfpDocument.builder()
                                     .sections(List.of())
                                     .entities(RfpEntities.builder()
                                                          .issueDate("2026-06-01")
                                                          .submissionDeadline("2026-03-01")
                                                          .build())
                                     .build();

        List<ClarificationTrigger> triggers = trigger.detect(
            doc,
            results,
            state
        );

        assertThat(triggers).hasSize(1);
        assertThat(triggers.getFirst().getType())
            .isEqualTo(QuestionType.CONTRADICTION_RESOLUTION);
    }

    @Test
    void shouldCapTriggersAtFiftyWhenManyFindings() {
        List<RuleFinding> findings = IntStream.rangeClosed(1, 60)
                                              .mapToObj(i -> finding(
                                                  String.format("RULE-%03d", i),
                                                  RuleSeverity.FATAL,
                                                  RuleStatus.FAIL
                                              ))
                                              .toList();

        RulePackResults results = resultsWithFindings(findings);
        ExtractionState state = stateWithConfidence(Map.of());
        RfpDocument doc = minimalDoc();

        List<ClarificationTrigger> triggers = trigger.detect(
            doc,
            results,
            state
        );

        assertThat(triggers).hasSize(50);
    }

    @Test
    void shouldNotTriggerForPassingFindings() {
        RulePackResults results = resultsWithFindings(List.of(
            finding("BD-ICT-001", RuleSeverity.FATAL, RuleStatus.PASS)
        ));
        ExtractionState state = stateWithConfidence(Map.of());
        RfpDocument doc = minimalDoc();

        List<ClarificationTrigger> triggers = trigger.detect(
            doc,
            results,
            state
        );

        assertThat(triggers).isEmpty();
    }

    @Test
    void shouldNotTriggerForMediumSeverityFailures() {
        RulePackResults results = resultsWithFindings(List.of(
            finding("BD-ICT-050", RuleSeverity.MEDIUM, RuleStatus.FAIL)
        ));
        ExtractionState state = stateWithConfidence(Map.of());
        RfpDocument doc = minimalDoc();

        List<ClarificationTrigger> triggers = trigger.detect(
            doc,
            results,
            state
        );

        assertThat(triggers).isEmpty();
    }

    private RuleFinding finding(
        String ruleId,
        RuleSeverity severity,
        RuleStatus status
    ) {
        return RuleFinding.builder()
                          .ruleId(ruleId)
                          .severity(severity)
                          .status(status)
                          .message(String.format("Rule %s check", ruleId))
                          .checkedAt(Instant.now())
                          .build();
    }

    private RulePackResults resultsWithFindings(List<RuleFinding> findings) {
        return RulePackResults.builder()
                              .packId("test-pack")
                              .packVersion("1.0.0")
                              .findings(findings)
                              .build();
    }

    private ExtractionState stateWithConfidence(Map<String, Double> map) {
        ExtractionState state = mock(ExtractionState.class);
        when(state.confidenceMap()).thenReturn(map);
        when(state.repairLog()).thenReturn(List.of());
        return state;
    }

    private RfpDocument minimalDoc() {
        return RfpDocument.builder()
                          .sections(List.of())
                          .entities(RfpEntities.builder().build())
                          .build();
    }
}
