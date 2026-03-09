package com.dsi.rfp.adapter.artifact;

import java.util.List;

public final class AuditReportTemplateModel {

    private AuditReportTemplateModel() {
    }

    public enum ConfidenceBand {
        HIGH("conf-high"),
        MEDIUM("conf-mid"),
        LOW("conf-low");

        private final String cssClass;

        ConfidenceBand(String cssClass) {
            this.cssClass = cssClass;
        }

        public String cssClass() {
            return cssClass;
        }
    }

    public record ReportView(
        String title,
        String procurementRef,
        String generatedAt,
        List<PageAuditView> pageSummaries,
        List<EntityAuditView> entitySummary,
        List<RepairAuditView> repairLog,
        List<RuleFindingAuditView> ruleFindings,
        AuditStats stats
    ) {
    }

    public record PageAuditView(
        int pageNumber,
        String classification,
        String method,
        double confidence,
        String confClass,
        int retries
    ) {
    }

    public record EntityAuditView(
        String name,
        String value,
        double confidence,
        String confClass
    ) {
    }

    public record RepairAuditView(
        String componentId,
        String strategy,
        int attempt,
        double before,
        double after,
        String outcome
    ) {
    }

    public record RuleFindingAuditView(
        String ruleId,
        String severity,
        String status,
        String message
    ) {
    }

    public record AuditStats(
        int totalPages,
        long digitalPages,
        long scannedPages,
        long mixedPages,
        int tablesFound,
        int entitiesExtracted,
        long fatalFail,
        long highFail
    ) {
    }
}
