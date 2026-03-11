package com.dsi.rfp.adapter.vision;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VisionTableResult(
    String caption,
    List<String> headers,
    List<List<String>> grid,
    double confidence
) {

    public VisionTableResult {
        caption = Objects.requireNonNullElse(
            caption,
            ""
        );
        headers = Objects.requireNonNullElseGet(
            headers,
            List::of
        );
        grid = Objects.requireNonNullElseGet(
            grid,
            List::of
        );
    }
}
