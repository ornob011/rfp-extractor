package com.dsi.rfp.adapter.artifact;

enum AmbiguityRegisterColumn {

    ISSUE_ID("Issue ID"),
    SEVERITY("Severity"),
    CATEGORY("Category"),
    DESCRIPTION("Description"),
    SOURCE_CLAUSE("Source Clause"),
    PAGE("Page"),
    RECOMMENDED_ACTION("Recommended Action");

    private final String header;

    AmbiguityRegisterColumn(String header) {
        this.header = header;
    }

    String header() {
        return header;
    }
}
