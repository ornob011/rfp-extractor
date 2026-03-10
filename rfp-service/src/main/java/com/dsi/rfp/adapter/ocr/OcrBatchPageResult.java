package com.dsi.rfp.adapter.ocr;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OcrBatchPageResult(
    @JsonProperty("page_number")
    int pageNumber,

    @JsonProperty("ocr_result")
    OcrResultDto ocrResult,

    LayoutDetectionDto layout,

    @JsonProperty("reading_order")
    ReadingOrderDto readingOrder,

    @JsonProperty("scanned_tables")
    List<OcrScannedTableDto> scannedTables
) {
}
