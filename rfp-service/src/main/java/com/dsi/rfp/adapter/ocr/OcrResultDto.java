package com.dsi.rfp.adapter.ocr;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OcrResultDto(
    String text,

    @JsonProperty("word_confidences")
    List<OcrWordDto> wordConfidences,

    @JsonProperty("page_confidence")
    double pageConfidence,

    @JsonProperty("word_count")
    int wordCount,

    @JsonProperty("extraction_method")
    String extractionMethod
) {
}
