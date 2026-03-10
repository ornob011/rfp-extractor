package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class SectionConfidence implements Serializable {

    private double score;
    private HeadingDetectionMethod method;
}
