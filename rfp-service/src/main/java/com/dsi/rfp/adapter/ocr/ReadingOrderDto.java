package com.dsi.rfp.adapter.ocr;

import com.dsi.rfp.domain.model.ReadingOrderMethod;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ReadingOrderDto(
    @JsonProperty("ordered_text")
    String orderedText,

    ReadingOrderMethod method
) {
}
