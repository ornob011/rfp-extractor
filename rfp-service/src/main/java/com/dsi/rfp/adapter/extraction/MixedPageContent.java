package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.PageExtractionMethod;

public record MixedPageContent(
    String text,
    double confidence,
    PageExtractionMethod method
) {

    public static MixedPageContent textLayer(
        String text,
        double confidence
    ) {
        return new MixedPageContent(
            text,
            confidence,
            PageExtractionMethod.TEXT_LAYER
        );
    }

    public static MixedPageContent textPlusOcr(
        String text,
        double confidence
    ) {
        return new MixedPageContent(
            text,
            confidence,
            PageExtractionMethod.TEXT_PLUS_OCR
        );
    }
}
