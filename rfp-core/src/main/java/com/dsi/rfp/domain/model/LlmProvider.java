package com.dsi.rfp.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LlmProvider {
    OPENROUTER("openrouter"),
    OLLAMA("ollama"),
    OPENAI("openai");

    private final String jsonValue;

    LlmProvider(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonCreator
    public static LlmProvider fromString(String value) {
        for (LlmProvider provider : values()) {
            if (provider.jsonValue.equalsIgnoreCase(value)
                || provider.name().equalsIgnoreCase(value)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown LlmProvider: " + value);
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
