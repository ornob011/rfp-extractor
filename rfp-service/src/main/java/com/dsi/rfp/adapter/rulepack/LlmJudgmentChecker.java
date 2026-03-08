package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.adapter.llm.PromptTemplateRenderer;
import com.dsi.rfp.domain.model.LlmJudgmentResult;
import com.dsi.rfp.domain.model.RuleDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmJudgmentChecker {

    private final LlmAdapter llmAdapter;
    private final RulePackConfig config;
    private final PromptTemplateRenderer promptTemplateRenderer;

    public LlmJudgmentResult check(
        RuleDefinition rule,
        String evidenceValue
    ) {
        long start = System.currentTimeMillis();

        String prompt = promptTemplateRenderer.render(
            config.promptTemplate(),
            Map.of(
                "criterion",
                resolveCriterion(rule)
            )
        );

        LlmJudgmentResult result = llmAdapter.judgeSnippet(
            prompt,
            evidenceValue
        );

        long latency = System.currentTimeMillis() - start;

        log.info(
            "Semantic rule {}: finding={}, confidence={}, latency_ms={}",
            rule.getId(),
            result.isFinding(),
            result.getConfidence(),
            latency
        );

        return result;
    }

    private String resolveCriterion(RuleDefinition rule) {
        return Optional.ofNullable(rule.getLlmPrompt())
                       .filter(prompt -> !prompt.isBlank())
                       .orElse(rule.getMessage());
    }
}
