package com.dsi.rfp.adapter.ocr;

import java.util.List;

public record OcrScannedTableDto(
    List<String> headers,
    List<List<String>> rows,
    double confidence,
    String method
) {
}
