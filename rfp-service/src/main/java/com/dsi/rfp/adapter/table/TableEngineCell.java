package com.dsi.rfp.adapter.table;

public record TableEngineCell(
    int row,
    int col,
    String value,
    int rowspan,
    int colspan,
    boolean isHeader
) {
}

