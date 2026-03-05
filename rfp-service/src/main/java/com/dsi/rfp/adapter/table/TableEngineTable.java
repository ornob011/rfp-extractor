package com.dsi.rfp.adapter.table;

import java.util.List;

public record TableEngineTable(
    String caption,
    List<String> headers,
    List<List<TableEngineCell>> grid,
    double confidence,
    String method
) {
}

