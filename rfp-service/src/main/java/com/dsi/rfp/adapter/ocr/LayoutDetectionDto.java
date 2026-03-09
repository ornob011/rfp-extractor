package com.dsi.rfp.adapter.ocr;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record LayoutDetectionDto(
    @JsonProperty("has_table")
    boolean hasTable,

    @JsonProperty("table_regions")
    List<BoundingBoxDto> tableRegions,

    @JsonProperty("text_regions")
    List<BoundingBoxDto> textRegions
) {
}
