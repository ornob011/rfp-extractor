package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExtractionConfidence {

    private final double score;
    private final String method;
}
