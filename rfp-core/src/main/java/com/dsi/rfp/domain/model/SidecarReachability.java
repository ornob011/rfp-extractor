package com.dsi.rfp.domain.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SidecarReachability {
    REACHABLE("reachable"),
    UNREACHABLE("unreachable");

    private final String jsonValue;

    SidecarReachability(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
