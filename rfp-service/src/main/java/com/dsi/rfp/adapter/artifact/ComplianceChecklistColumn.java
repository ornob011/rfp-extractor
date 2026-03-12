package com.dsi.rfp.adapter.artifact;

enum ComplianceChecklistColumn {

    SERIAL_NUMBER("Sl."),
    TITLE("Title"),
    ANSWER("Answer");

    private final String header;

    ComplianceChecklistColumn(String header) {
        this.header = header;
    }

    String header() {
        return header;
    }
}
