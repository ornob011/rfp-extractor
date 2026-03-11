package com.dsi.rfp.adapter.ocr;

import com.dsi.rfp.adapter.vision.VisionTableResult;
import com.dsi.rfp.domain.model.PageExtractionMethod;

import java.util.List;
import java.util.Objects;

public record ScannedPageExtractionResult(
    int pageNum,
    String text,
    double confidence,
    int wordCount,
    PageExtractionMethod extractionMethod,
    List<VisionTableResult> tables
) {

    public ScannedPageExtractionResult {
        tables = Objects.requireNonNullElseGet(
            tables,
            List::of
        );
    }
}
