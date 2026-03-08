package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RulePackRunner {

    private static final Map<Boolean, RuleStatus> STRUCTURAL_STATUS = Map.of(
        Boolean.TRUE,
        RuleStatus.PASS,
        Boolean.FALSE,
        RuleStatus.FAIL
    );

    private final JmesPathEvaluator jmesPathEvaluator;
    private final LlmJudgmentChecker llmJudgmentChecker;

    public RulePackResults run(
        RulePackDefinition pack,
        String rfpJson
    ) {
        long start = System.currentTimeMillis();

        List<RuleFinding> findings = pack.getRules()
                                         .stream()
                                         .map(rule -> evaluateRule(rule, rfpJson))
                                         .toList();

        Map<RuleSeverity, Integer> summary = buildSummary(findings);
        long duration = System.currentTimeMillis() - start;

        logResults(pack, findings, duration);

        return RulePackResults.builder()
                              .packId(pack.getPackId())
                              .packVersion(pack.getPackVersion())
                              .runTimestamp(Instant.now())
                              .summary(summary)
                              .findings(findings)
                              .build();
    }

    private RuleFinding evaluateRule(
        RuleDefinition rule,
        String rfpJson
    ) {
        return switch (rule.getCheckType()) {
            case STRUCTURAL -> evaluateStructural(rule, rfpJson);
            case SEMANTIC -> evaluateSemantic(rule, rfpJson);
        };
    }

    private RuleFinding evaluateStructural(
        RuleDefinition rule,
        String rfpJson
    ) {
        boolean conditionMet = jmesPathEvaluator.evaluateAsBoolean(
            rule.getCondition(),
            rfpJson
        );

        String evidence = jmesPathEvaluator.extractEvidence(
            rule.getEvidencePath(),
            rfpJson
        ).orElse(null);

        RuleStatus status = STRUCTURAL_STATUS.get(conditionMet);

        return buildFinding(rule, status, evidence);
    }

    private RuleFinding evaluateSemantic(
        RuleDefinition rule,
        String rfpJson
    ) {
        String evidence = jmesPathEvaluator.extractEvidence(
            rule.getEvidencePath(),
            rfpJson
        ).orElse(null);

        if (evidence == null || evidence.isBlank()) {
            return buildFinding(rule, RuleStatus.SKIPPED, null);
        }

        LlmJudgmentResult judgment = llmJudgmentChecker.check(rule, evidence);

        return buildFinding(rule, judgment.getStatus(), evidence);
    }

    private RuleFinding buildFinding(
        RuleDefinition rule,
        RuleStatus status,
        String evidence
    ) {
        return RuleFinding.builder()
                          .ruleId(rule.getId())
                          .severity(rule.getSeverity())
                          .status(status)
                          .message(rule.getMessage())
                          .evidence(evidence)
                          .checkedAt(Instant.now())
                          .build();
    }

    private Map<RuleSeverity, Integer> buildSummary(List<RuleFinding> findings) {
        Map<RuleSeverity, Integer> summary = new EnumMap<>(RuleSeverity.class);

        findings.stream()
                .filter(f -> f.getStatus() == RuleStatus.FAIL)
                .forEach(f -> summary.merge(f.getSeverity(), 1, Integer::sum));

        return summary;
    }

    private void logResults(
        RulePackDefinition pack,
        List<RuleFinding> findings,
        long durationMs
    ) {
        long failCount = findings.stream()
                                 .filter(f -> f.getStatus() == RuleStatus.FAIL)
                                 .count();
        long passCount = findings.stream()
                                 .filter(f -> f.getStatus() == RuleStatus.PASS)
                                 .count();
        long skipCount = findings.stream()
                                 .filter(f -> f.getStatus() == RuleStatus.SKIPPED)
                                 .count();

        log.info(
            "Rule pack {} completed: {} findings ({} FAIL, {} PASS, {} SKIPPED) in {}ms",
            pack.getPackId(),
            findings.size(),
            failCount,
            passCount,
            skipCount,
            durationMs
        );
    }
}
