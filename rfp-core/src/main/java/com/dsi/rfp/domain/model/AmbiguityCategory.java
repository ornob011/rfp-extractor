package com.dsi.rfp.domain.model;

public enum AmbiguityCategory {

    MISSING_FIELD("Missing Field"),
    VAGUE_REQUIREMENT("Vague Requirement"),
    MINOR_GAP("Minor Gap");

    private final String label;

    AmbiguityCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
