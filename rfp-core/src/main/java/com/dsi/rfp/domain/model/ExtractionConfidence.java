package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class ExtractionConfidence implements Serializable {

    private final double score;
    private final String method;
}
