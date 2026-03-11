package com.dsi.rfp.adapter.vision;

import com.dsi.rfp.domain.model.PageExtractionMethod;

import java.util.List;
import java.util.Objects;

public record VisionPageExtractionResult(
    int pageNum,
    String text,
    double confidence,
    int wordCount,
    PageExtractionMethod extractionMethod,
    List<VisionTableResult> tables
) {

    public VisionPageExtractionResult {
        tables = Objects.requireNonNullElseGet(
            tables,
            List::of
        );
    }
}
