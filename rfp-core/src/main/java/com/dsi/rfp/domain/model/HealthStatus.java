package com.dsi.rfp.domain.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum HealthStatus {
    UP("UP"),
    DEGRADED("DEGRADED");

    private final String jsonValue;

    HealthStatus(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
