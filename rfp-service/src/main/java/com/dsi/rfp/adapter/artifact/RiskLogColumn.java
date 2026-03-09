package com.dsi.rfp.adapter.artifact;

enum RiskLogColumn {

    INDEX("#"),
    RISK_ASSUMPTION("Risk/Assumption"),
    SOURCE("Source"),
    IMPACT("Impact"),
    MITIGATION_SUGGESTION("Mitigation Suggestion"),
    OWNER("Owner");

    private final String header;

    RiskLogColumn(String header) {
        this.header = header;
    }

    String header() {
        return header;
    }
}
