package com.dsi.rfp.domain.model;

import java.util.List;

public record TableEngineTable(
    String caption,
    List<String> headers,
    List<List<TableEngineCell>> grid,
    double confidence,
    String method
) {
}
