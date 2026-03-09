package com.dsi.rfp.adapter.ocr;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OcrPageRequest(
    @JsonProperty("image_base64")
    String imageBase64,

    String lang,

    int dpi,

    @JsonProperty("document_path")
    String documentPath,

    @JsonProperty("page_number")
    Integer pageNumber
) {
    public OcrPageRequest(
        String imageBase64,
        String lang,
        int dpi
    ) {
        this(
            imageBase64,
            lang,
            dpi,
            null,
            null
        );
    }
}
