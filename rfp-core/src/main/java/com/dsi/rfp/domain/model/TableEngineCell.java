package com.dsi.rfp.domain.model;

public record TableEngineCell(
    int row,
    int col,
    String value,
    int rowspan,
    int colspan,
    boolean isHeader
) {
}
