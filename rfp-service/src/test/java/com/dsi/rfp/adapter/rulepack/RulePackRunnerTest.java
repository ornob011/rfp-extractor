package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RulePackRunnerTest {

    private static final String RFP_JSON = "{}";
    @Mock
    private JmesPathEvaluator jmesPathEvaluator;
    @Mock
    private LlmJudgmentChecker llmJudgmentChecker;
    @InjectMocks
    private RulePackRunner runner;

    @Test
    void shouldReturnPassFindingWhenStructuralConditionTrue() {
        RuleDefinition rule = structuralRule("BD-ICT-001", RuleSeverity.FATAL);
        RulePackDefinition pack = packWith(rule);

        when(jmesPathEvaluator.evaluateAsBoolean(anyString(), anyString())).thenReturn(true);
        when(jmesPathEvaluator.extractEvidence(anyString(), anyString()))
            .thenReturn(Optional.of("ICT Procurement"));

        RulePackResults results = runner.run(pack, RFP_JSON);

        assertThat(results.getFindings()).hasSize(1);
        assertThat(results.getFindings().getFirst().getStatus()).isEqualTo(RuleStatus.PASS);
    }

    @Test
    void shouldReturnFailFindingWhenStructuralConditionFalse() {
        RuleDefinition rule = structuralRule("BD-ICT-001", RuleSeverity.FATAL);
        RulePackDefinition pack = packWith(rule);

        when(jmesPathEvaluator.evaluateAsBoolean(anyString(), anyString())).thenReturn(false);
        when(jmesPathEvaluator.extractEvidence(anyString(), anyString()))
            .thenReturn(Optional.empty());

        RulePackResults results = runner.run(pack, RFP_JSON);

        assertThat(results.getFindings()).hasSize(1);

        RuleFinding finding = results.getFindings().getFirst();
        assertThat(finding.getStatus()).isEqualTo(RuleStatus.FAIL);
        assertThat(finding.getSeverity()).isEqualTo(RuleSeverity.FATAL);
    }

    @Test
    void shouldReturnFailFindingForSemanticRuleWhenLlmFindingTrue() {
        RuleDefinition rule = semanticRule("BD-ICT-056", RuleSeverity.HIGH);
        RulePackDefinition pack = packWith(rule);

        when(jmesPathEvaluator.extractEvidence(anyString(), anyString()))
            .thenReturn(Optional.of("vague scope description"));

        LlmJudgmentResult judgment = LlmJudgmentResult.builder()
                                                      .finding(true)
                                                      .status(RuleStatus.FAIL)
                                                      .confidence(0.9)
                                                      .build();
        when(llmJudgmentChecker.check(eq(rule), anyString())).thenReturn(judgment);

        RulePackResults results = runner.run(pack, RFP_JSON);

        assertThat(results.getFindings().getFirst().getStatus()).isEqualTo(RuleStatus.FAIL);
    }

    @Test
    void shouldReturnPassFindingForSemanticRuleWhenLlmFindingFalse() {
        RuleDefinition rule = semanticRule("BD-ICT-056", RuleSeverity.HIGH);
        RulePackDefinition pack = packWith(rule);

        when(jmesPathEvaluator.extractEvidence(anyString(), anyString()))
            .thenReturn(Optional.of("clear specific scope"));

        LlmJudgmentResult judgment = LlmJudgmentResult.builder()
                                                      .finding(false)
                                                      .status(RuleStatus.PASS)
                                                      .confidence(0.95)
                                                      .build();
        when(llmJudgmentChecker.check(eq(rule), anyString())).thenReturn(judgment);

        RulePackResults results = runner.run(pack, RFP_JSON);

        assertThat(results.getFindings().getFirst().getStatus()).isEqualTo(RuleStatus.PASS);
    }

    @Test
    void shouldReturnSkippedWhenSemanticEvidenceIsEmpty() {
        RuleDefinition rule = semanticRule("BD-ICT-056", RuleSeverity.HIGH);
        RulePackDefinition pack = packWith(rule);

        when(jmesPathEvaluator.extractEvidence(anyString(), anyString()))
            .thenReturn(Optional.empty());

        RulePackResults results = runner.run(pack, RFP_JSON);

        assertThat(results.getFindings().getFirst().getStatus()).isEqualTo(RuleStatus.SKIPPED);
        verifyNoInteractions(llmJudgmentChecker);
    }

    @Test
    void shouldBuildCorrectSummaryWhenMixedFindings() {
        RuleDefinition fatal = structuralRule("BD-ICT-001", RuleSeverity.FATAL);
        RuleDefinition high = structuralRule("BD-ICT-003", RuleSeverity.HIGH);
        RuleDefinition pass = structuralRule("BD-ICT-004", RuleSeverity.FATAL);

        RulePackDefinition pack = RulePackDefinition.builder()
                                                    .packId("bd-govt-ict-v1")
                                                    .packVersion("1.0.0")
                                                    .rules(List.of(fatal, high, pass))
                                                    .build();

        when(jmesPathEvaluator.evaluateAsBoolean(eq("cond"), anyString()))
            .thenReturn(false, false, true);
        when(jmesPathEvaluator.extractEvidence(anyString(), anyString()))
            .thenReturn(Optional.empty());

        RulePackResults results = runner.run(pack, RFP_JSON);

        assertThat(results.getSummary().get(RuleSeverity.FATAL)).isEqualTo(1);
        assertThat(results.getSummary().get(RuleSeverity.HIGH)).isEqualTo(1);
    }

    @Test
    void shouldRunAllRulesWhenPackHasMultipleRules() {
        RuleDefinition r1 = structuralRule("BD-ICT-001", RuleSeverity.FATAL);
        RuleDefinition r2 = structuralRule("BD-ICT-003", RuleSeverity.HIGH);
        RuleDefinition r3 = structuralRule("BD-ICT-010", RuleSeverity.MEDIUM);

        RulePackDefinition pack = RulePackDefinition.builder()
                                                    .packId("bd-govt-ict-v1")
                                                    .packVersion("1.0.0")
                                                    .rules(List.of(r1, r2, r3))
                                                    .build();

        when(jmesPathEvaluator.evaluateAsBoolean(anyString(), anyString())).thenReturn(true);
        when(jmesPathEvaluator.extractEvidence(anyString(), anyString()))
            .thenReturn(Optional.of("value"));

        RulePackResults results = runner.run(pack, RFP_JSON);

        assertThat(results.getFindings()).hasSize(3);
    }

    private RuleDefinition structuralRule(String id, RuleSeverity severity) {
        return RuleDefinition.builder()
                             .id(id)
                             .name("Test Rule")
                             .checkType(CheckType.STRUCTURAL)
                             .severity(severity)
                             .condition("cond")
                             .evidencePath("path")
                             .message("Test message")
                             .build();
    }

    private RuleDefinition semanticRule(String id, RuleSeverity severity) {
        return RuleDefinition.builder()
                             .id(id)
                             .name("Test Semantic Rule")
                             .checkType(CheckType.SEMANTIC)
                             .severity(severity)
                             .llmPrompt("Check something")
                             .evidencePath("entities.scopeOfWork")
                             .message("Test message")
                             .build();
    }

    private RulePackDefinition packWith(RuleDefinition rule) {
        return RulePackDefinition.builder()
                                 .packId("bd-govt-ict-v1")
                                 .packVersion("1.0.0")
                                 .rules(List.of(rule))
                                 .build();
    }
}
