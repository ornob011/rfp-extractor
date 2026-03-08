package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.adapter.llm.PromptTemplateRenderer;
import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmJudgmentCheckerTest {

    @Mock
    private LlmAdapter llmAdapter;

    @Mock
    private RulePackConfig config;

    private LlmJudgmentChecker createChecker() {
        when(config.promptTemplate()).thenReturn("Criterion: {{criterion}}");

        return new LlmJudgmentChecker(
            llmAdapter,
            config,
            new PromptTemplateRenderer()
        );
    }

    @Test
    void shouldReturnFailWhenLlmFindingIsTrue() {
        LlmJudgmentResult llmResult = LlmJudgmentResult.builder()
                                                       .finding(true)
                                                       .explanation("vague")
                                                       .confidence(0.9)
                                                       .status(RuleStatus.FAIL)
                                                       .build();

        when(llmAdapter.judgeSnippet(anyString(), anyString())).thenReturn(llmResult);

        LlmJudgmentChecker checker = createChecker();
        RuleDefinition rule = buildSemanticRule();

        LlmJudgmentResult result = checker.check(rule, "some evidence");

        assertThat(result.getStatus()).isEqualTo(RuleStatus.FAIL);
        assertThat(result.isFinding()).isTrue();
    }

    @Test
    void shouldReturnPassWhenLlmFindingIsFalse() {
        LlmJudgmentResult llmResult = LlmJudgmentResult.builder()
                                                       .finding(false)
                                                       .explanation("clear")
                                                       .confidence(0.95)
                                                       .status(RuleStatus.PASS)
                                                       .build();

        when(llmAdapter.judgeSnippet(anyString(), anyString())).thenReturn(llmResult);

        LlmJudgmentChecker checker = createChecker();
        RuleDefinition rule = buildSemanticRule();

        LlmJudgmentResult result = checker.check(rule, "clear scope");

        assertThat(result.getStatus()).isEqualTo(RuleStatus.PASS);
    }

    @Test
    void shouldPropagateWhenLlmThrowsException() {
        when(llmAdapter.judgeSnippet(anyString(), anyString()))
            .thenThrow(new CompletionException(new RuntimeException("timeout")));

        LlmJudgmentChecker checker = createChecker();
        RuleDefinition rule = buildSemanticRule();

        assertThatThrownBy(() -> checker.check(rule, "evidence"))
            .isInstanceOf(CompletionException.class);
    }

    private RuleDefinition buildSemanticRule() {
        return RuleDefinition.builder()
                             .id("BD-ICT-056")
                             .name("Scope Specific")
                             .checkType(CheckType.SEMANTIC)
                             .severity(RuleSeverity.HIGH)
                             .llmPrompt("Check if scope is specific")
                             .evidencePath("entities.scopeOfWork")
                             .message("Scope may be vague")
                             .build();
    }
}
