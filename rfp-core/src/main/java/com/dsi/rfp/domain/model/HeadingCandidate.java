package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HeadingCandidate {

    private int pageNumber;
    private String text;
    private int level;
    private float startY;
    private String fontName;
    private float fontSize;
    private HeadingDetectionMethod detectedBy;
}
