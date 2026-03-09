package com.dsi.rfp.adapter.ocr;

import com.dsi.rfp.domain.model.PageExtractionMethod;

public record ScannedPageExtractionResult(
    int pageNum,
    String text,
    double confidence,
    int wordCount,
    PageExtractionMethod extractionMethod
) {
}
