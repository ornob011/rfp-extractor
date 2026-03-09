package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.domain.model.PageClassification;
import com.dsi.rfp.domain.model.PageExtractionMethod;

public record PageDetailDto(
    int pageNum,
    PageClassification classification,
    PageExtractionMethod extractionMethod,
    double confidence
) {
}
