package com.dsi.rfp.domain.model;

public enum RepairOutcome {
    IMPROVED("IMPROVED"),
    NOT_IMPROVED("NOT IMPROVED"),
    MAX_RETRIES("MAX RETRIES");

    private final String label;

    RepairOutcome(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
