package com.dsi.rfp.adapter.ocr;

import java.util.List;

public record OcrBatchResponse(
    List<OcrBatchPageResult> results
) {
}
