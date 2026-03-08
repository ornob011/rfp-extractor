package com.dsi.rfp.adapter.ocr;

public record BoundingBoxDto(
    double x,
    double y,
    double width,
    double height
) {
}
