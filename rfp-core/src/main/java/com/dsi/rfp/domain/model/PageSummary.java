package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class PageSummary implements Serializable {

    private final int pageNumber;
    private final PageClassification classification;
    private final double charDensity;
    private final double rasterCoverage;
    private final int charCount;
    private final double pageWidth;
    private final double pageHeight;
    private final double pageAreaPixels;
    private final double totalImageArea;
}

