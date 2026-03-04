package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmbeddedImageInfo {

    private final int pageNumber;
    private final float x;
    private final float y;
    private final float width;
    private final float height;

    public double area() {
        return (double) width * height;
    }
}
