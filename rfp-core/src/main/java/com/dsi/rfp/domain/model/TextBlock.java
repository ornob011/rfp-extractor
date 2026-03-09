package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TextBlock {

    private final float x;
    private final float y;
    private final float width;
    private final float height;
    private final String text;
    private final float fontSize;
    private final String fontName;
}
