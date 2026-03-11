package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.vision.VisionTableResult;
import com.dsi.rfp.domain.model.PageExtractionMethod;

import java.util.List;
import java.util.Objects;

public record MixedPageContent(
    String text,
    double confidence,
    PageExtractionMethod method,
    List<VisionTableResult> tables
) {

    public MixedPageContent {
        tables = Objects.requireNonNullElseGet(
            tables,
            List::of
        );
    }

    public static MixedPageContent textLayer(
        String text,
        double confidence
    ) {
        return new MixedPageContent(
            text,
            confidence,
            PageExtractionMethod.TEXT_LAYER,
            List.of()
        );
    }

    public static MixedPageContent textPlusOcr(
        String text,
        double confidence,
        List<VisionTableResult> tables
    ) {
        return new MixedPageContent(
            text,
            confidence,
            PageExtractionMethod.TEXT_PLUS_OCR,
            tables
        );
    }
}
