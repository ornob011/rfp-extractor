package com.dsi.rfp.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum TableExtractionStrategy {
    VLM("vlm"),
    LATTICE("lattice"),
    STREAM("stream");

    private final String jsonValue;

    TableExtractionStrategy(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static TableExtractionStrategy fromString(String value) {
        return Arrays.stream(values())
                     .filter(strategy -> strategy.jsonValue.equalsIgnoreCase(value)
                                         || strategy.name().equalsIgnoreCase(value))
                     .findFirst()
                     .orElseThrow(() -> new IllegalArgumentException(
                         String.format("Unknown TableExtractionStrategy: %s", value)
                     ));
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
