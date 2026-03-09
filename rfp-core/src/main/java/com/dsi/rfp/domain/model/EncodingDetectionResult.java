package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EncodingDetectionResult {

    boolean suspectedLegacy;
    double confidence;
    String reason;
}
