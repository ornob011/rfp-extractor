package com.dsi.rfp.adapter.vision;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VisionPageResult(
    String text,
    double confidence,
    List<VisionTableResult> tables,
    @JsonProperty("has_table") boolean hasTable
) {

    public VisionPageResult {
        text = Objects.requireNonNullElse(
            text,
            ""
        );
        tables = Objects.requireNonNullElseGet(
            tables,
            List::of
        );
    }
}
