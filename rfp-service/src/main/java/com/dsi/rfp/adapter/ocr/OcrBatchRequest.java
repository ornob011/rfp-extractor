package com.dsi.rfp.adapter.ocr;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OcrBatchRequest(
    @JsonProperty("document_base64")
    String documentBase64,

    List<Integer> pages,

    int dpi,

    String lang
) {
}
