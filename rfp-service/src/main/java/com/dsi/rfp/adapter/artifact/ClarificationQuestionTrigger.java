package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClarificationQuestionTrigger {

    private final ArtifactGenerationConfig config;

    public List<ClarificationTrigger> detect(
        RfpDocument doc,
        RulePackResults results,
        ExtractionState state
    ) {
        List<ClarificationTrigger> triggers = Stream.of(
                                                        fromRuleFindings(results),
                                                        fromLowConfidenceEntities(state),
                                                        fromContradictions(doc)
                                                    )
                                                    .flatMap(List::stream)
                                                    .limit(config.maxTriggers())
                                                    .toList();

        logTriggerSummary(triggers);

        return triggers;
    }

    private List<ClarificationTrigger> fromRuleFindings(RulePackResults results) {
        return results.getFindings().stream()
                      .filter(f -> f.getStatus() == RuleStatus.FAIL)
                      .filter(f -> isTriggerSeverity(f.getSeverity()))
                      .map(this::toRuleTrigger)
                      .toList();
    }

    private boolean isTriggerSeverity(RuleSeverity severity) {
        return switch (severity) {
            case FATAL, HIGH -> true;
            case MEDIUM, LOW, INFO -> false;
        };
    }

    private ClarificationTrigger toRuleTrigger(RuleFinding finding) {
        QuestionType type = switch (finding.getSeverity()) {
            case FATAL, HIGH -> QuestionType.MANDATORY_CLARIFICATION;
            case MEDIUM, LOW, INFO -> QuestionType.AMBIGUITY;
        };

        return ClarificationTrigger.builder()
                                   .type(type)
                                   .ruleId(finding.getRuleId())
                                   .context(finding.getMessage())
                                   .build();
    }

    private List<ClarificationTrigger> fromLowConfidenceEntities(
        ExtractionState state
    ) {
        Map<String, Double> confidenceMap = state.confidenceMap();

        return confidenceMap.entrySet().stream()
                            .filter(e -> e.getValue() < config.lowConfidenceThreshold())
                            .map(this::toConfidenceTrigger)
                            .toList();
    }

    private ClarificationTrigger toConfidenceTrigger(
        Map.Entry<String, Double> entry
    ) {
        return ClarificationTrigger.builder()
                                   .type(QuestionType.CONFIRMATION)
                                   .entityField(entry.getKey())
                                   .context(String.format(
                                       "Entity '%s' extracted with low confidence (%.0f%%)",
                                       entry.getKey(),
                                       entry.getValue() * 100
                                   ))
                                   .build();
    }

    private List<ClarificationTrigger> fromContradictions(RfpDocument doc) {
        List<ClarificationTrigger> triggers = new ArrayList<>();
        RfpEntities entities = doc.getEntities();

        if (entities == null) {
            return triggers;
        }

        checkDateContradiction(
            entities.getIssueDate(),
            entities.getSubmissionDeadline(),
            triggers
        );

        return triggers;
    }

    private void checkDateContradiction(
        String issueDate,
        String submissionDeadline,
        List<ClarificationTrigger> triggers
    ) {
        if (issueDate == null || submissionDeadline == null) {
            return;
        }

        LocalDate issued = tryParseDate(issueDate);
        LocalDate deadline = tryParseDate(submissionDeadline);

        if (issued == null || deadline == null) {
            return;
        }

        if (!issued.isBefore(deadline)) {
            triggers.add(
                ClarificationTrigger.builder()
                                    .type(QuestionType.CONTRADICTION_RESOLUTION)
                                    .context(String.format(
                                        "Issue date (%s) is not before submission deadline (%s)",
                                        issueDate,
                                        submissionDeadline
                                    ))
                                    .entityField("issueDate")
                                    .build()
            );
        }
    }

    private LocalDate tryParseDate(String dateStr) {
        try {
            return DateUtils.parseDateStrictly(
                                dateStr.trim(),
                                config.dateFormatPatterns().toArray(String[]::new)
                            )
                            .toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate();
        } catch (java.text.ParseException exception) {
            return null;
        }
    }

    private void logTriggerSummary(List<ClarificationTrigger> triggers) {
        long mandatory = triggers.stream()
                                 .filter(t -> t.getType() == QuestionType.MANDATORY_CLARIFICATION)
                                 .count();
        long confirmation = triggers.stream()
                                    .filter(t -> t.getType() == QuestionType.CONFIRMATION)
                                    .count();
        long ambiguity = triggers.stream()
                                 .filter(t -> t.getType() == QuestionType.AMBIGUITY)
                                 .count();
        long contradiction = triggers.stream()
                                     .filter(t -> t.getType() == QuestionType.CONTRADICTION_RESOLUTION)
                                     .count();

        log.info(
            "Identified {} clarification triggers: {} MANDATORY, {} CONFIRMATION, {} AMBIGUITY, {} CONTRADICTION",
            triggers.size(),
            mandatory,
            confirmation,
            ambiguity,
            contradiction
        );
    }
}
