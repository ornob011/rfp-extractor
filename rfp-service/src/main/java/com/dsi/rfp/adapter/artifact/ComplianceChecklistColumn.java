package com.dsi.rfp.adapter.artifact;

enum ComplianceChecklistColumn {

    INDEX("#"),
    REQUIREMENT("Requirement"),
    SOURCE_CLAUSE("Source Clause"),
    PAGE("Page"),
    MANDATORY("Mandatory?"),
    COMPLIANCE_STATUS("Compliance Status");

    private final String header;

    ComplianceChecklistColumn(String header) {
        this.header = header;
    }

    String header() {
        return header;
    }
}
