package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.agent.EntityFieldReader;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class AuditReportModelFactory {

    private final ArtifactGenerationConfig config;
    private final EntityFieldReader entityFieldReader;
    private final ArtifactDocumentContextResolver contextResolver;

    AuditReportTemplateModel.ReportView create(
        RfpDocument document,
        ExtractionState state,
        RulePackResults results
    ) {
        Map<String, Object> entityValues = entityValues(document);

        return new AuditReportTemplateModel.ReportView(
            contextResolver.title(document),
            contextResolver.procurementReference(document),
            Instant.now().toString(),
            document.getPageClassifications().stream()
                    .map(page -> pageSummary(
                        page,
                        document,
                        state
                    ))
                    .toList(),
            document.getConfidenceMap().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entitySummary(
                        entry,
                        entityValues
                    ))
                    .toList(),
            state.repairLog().stream()
                 .map(this::repairEntry)
                 .toList(),
            results.getFindings().stream()
                   .sorted(Comparator.comparing(RuleFinding::getSeverity))
                   .map(this::ruleFinding)
                   .toList(),
            statistics(
                document,
                results
            )
        );
    }

    private AuditReportTemplateModel.PageAuditView pageSummary(
        PageSummary page,
        RfpDocument document,
        ExtractionState state
    ) {
        double confidence = document.getPageConfidences()
                                    .getOrDefault(page.getPageNumber(), 1.0);
        PageExtractionMethod method = Optional.ofNullable(
            document.getPageExtractionMethods().get(page.getPageNumber())
        ).orElse(PageExtractionMethod.TEXT_LAYER);

        return new AuditReportTemplateModel.PageAuditView(
            page.getPageNumber(),
            page.getClassification().name(),
            method.name(),
            confidence,
            confidenceBand(confidence).cssClass(),
            retryCount(
                state,
                page.getPageNumber()
            )
        );
    }

    private AuditReportTemplateModel.EntityAuditView entitySummary(
        Map.Entry<String, Double> entry,
        Map<String, Object> entityValues
    ) {
        double confidence = entry.getValue();
        Object rawValue = entityValues.get(entry.getKey());

        return new AuditReportTemplateModel.EntityAuditView(
            entry.getKey(),
            truncateValue(rawValue),
            confidence,
            confidenceBand(confidence).cssClass()
        );
    }

    private AuditReportTemplateModel.RepairAuditView repairEntry(RepairLogEntry entry) {
        return new AuditReportTemplateModel.RepairAuditView(
            entry.getComponentId(),
            entry.getStrategy().name(),
            entry.getAttemptNumber(),
            entry.getBeforeConfidence(),
            entry.getAfterConfidence(),
            entry.getOutcome().name()
        );
    }

    private AuditReportTemplateModel.RuleFindingAuditView ruleFinding(RuleFinding finding) {
        return new AuditReportTemplateModel.RuleFindingAuditView(
            finding.getRuleId(),
            finding.getSeverity().name(),
            finding.getStatus().name(),
            finding.getMessage()
        );
    }

    private AuditReportTemplateModel.AuditStats statistics(
        RfpDocument document,
        RulePackResults results
    ) {
        Map<PageClassification, Long> pageCounts = document.getPageClassifications().stream()
                                                           .collect(Collectors.groupingBy(
                                                               PageSummary::getClassification,
                                                               Collectors.counting()
                                                           ));

        return new AuditReportTemplateModel.AuditStats(
            document.getPageClassifications().size(),
            pageCounts.getOrDefault(PageClassification.DIGITAL, 0L),
            pageCounts.getOrDefault(PageClassification.SCANNED, 0L),
            pageCounts.getOrDefault(PageClassification.MIXED, 0L),
            document.getTables().size(),
            document.getConfidenceMap().size(),
            failureCount(
                results,
                RuleSeverity.FATAL
            ),
            failureCount(
                results,
                RuleSeverity.HIGH
            )
        );
    }

    private int retryCount(
        ExtractionState state,
        int pageNumber
    ) {
        return (int) state.repairLog().stream()
                          .filter(entry -> entry.getComponentId().contains(
                              String.valueOf(pageNumber)
                          ))
                          .count();
    }

    private long failureCount(
        RulePackResults results,
        RuleSeverity severity
    ) {
        return results.getFindings().stream()
                      .filter(finding -> finding.getSeverity() == severity)
                      .filter(finding -> finding.getStatus() == RuleStatus.FAIL)
                      .count();
    }

    private Map<String, Object> entityValues(RfpDocument document) {
        return Optional.ofNullable(document.getEntities())
                       .map(entityFieldReader::readAllFields)
                       .orElse(Map.of());
    }

    private String truncateValue(Object value) {
        String resolvedValue = contextResolver.displayValue(value);

        return Optional.of(resolvedValue)
                       .filter(text -> text.length() <= config.auditValueTruncateLength())
                       .orElseGet(() -> String.format(
                           "%s...",
                           resolvedValue.substring(
                               0,
                               config.auditValueTruncateLength()
                           )
                       ));
    }

    private AuditReportTemplateModel.ConfidenceBand confidenceBand(double confidence) {
        return config.auditConfidenceBands()
                     .floorEntry(confidence)
                     .getValue();
    }
}
