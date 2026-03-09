package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.adapter.ocr.LayoutDetectionDto;
import com.dsi.rfp.adapter.ocr.OcrResultDto;

public record MixedPageMergeInput(
    String orderedText,
    OcrResultDto ocrResult,
    LayoutDetectionDto layout,
    double textLayerQuality
) {
}
