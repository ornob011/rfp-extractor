package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FontInfo {

    private final String fontName;
    private final float minFontSize;
    private final float maxFontSize;
    private final float averageFontSize;
    private final int occurrenceCount;
}
