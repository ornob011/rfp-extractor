package com.dsi.rfp.adapter.ocr;

public record OcrWordDto(
    String text,
    double confidence,
    BoundingBoxDto bbox
) {
}
