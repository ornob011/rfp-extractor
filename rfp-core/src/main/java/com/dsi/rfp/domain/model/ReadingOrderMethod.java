package com.dsi.rfp.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ReadingOrderMethod {
    PDFPLUMBER_LAYOUT("pdfplumber_layout"),
    OCR_TEXT_FLOW("ocr_text_flow");

    private final String value;

    ReadingOrderMethod(
        String value
    ) {
        this.value = value;
    }

    @JsonCreator
    public static ReadingOrderMethod fromValue(
        String value
    ) {
        return Arrays.stream(values())
                     .filter(method -> method.value.equals(value))
                     .findFirst()
                     .orElseThrow(() -> new IllegalArgumentException(
                         String.format("Unknown reading order method: %s", value)
                     ));
    }

    @JsonValue
    public String value() {
        return value;
    }
}
