package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SectionConfidence {

    private double score;
    private HeadingDetectionMethod method;
}
