package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

abstract class RulePackFixtureSupport {

    private static final RulePackConfig CONFIG = new RulePackConfig(new EntitySignalReader());
    private static final RulePackLoader LOADER = new RulePackLoader(
        CONFIG,
        new PathMatchingResourcePatternResolver()
    );
    private static final LlmJudgmentResult DEFAULT_JUDGMENT = LlmJudgmentResult.builder()
                                                                               .finding(false)
                                                                               .status(RuleStatus.PASS)
                                                                               .confidence(1.0d)
                                                                               .build();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LlmJudgmentChecker llmJudgmentChecker = mock(LlmJudgmentChecker.class);
    private final RulePackRunner runner = new RulePackRunner(
        new JmesPathEvaluator(objectMapper),
        llmJudgmentChecker
    );

    protected RulePackFixtureSupport() {
        when(llmJudgmentChecker.check(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(DEFAULT_JUDGMENT);
    }

    protected RulePackDefinition loadPack(String packId) {
        return LOADER.loadAll()
                     .get(packId);
    }

    protected RulePackResults runPack(
        RulePackDefinition pack,
        RfpDocument document
    ) throws JsonProcessingException {
        return runner.run(
            pack,
            objectMapper.writeValueAsString(document)
        );
    }

    protected RuleFinding findingById(
        RulePackResults results,
        String ruleId
    ) {
        return Optional.of(results.getFindings())
                       .flatMap(findings -> findings.stream()
                                                    .filter(finding -> finding.getRuleId().equals(ruleId))
                                                    .findFirst())
                       .orElseThrow(() -> new AssertionError(
                           String.format("Missing finding for rule %s", ruleId)
                       ));
    }

    protected void assertRuleExists(
        RulePackDefinition pack,
        String ruleId
    ) {
        assertThat(pack.getRules())
            .extracting(RuleDefinition::getId)
            .contains(ruleId);
    }
}
