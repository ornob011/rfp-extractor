package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TableCell {

    private final int row;
    private final int col;
    private final String value;

    @Builder.Default
    private final int rowspan = 1;

    @Builder.Default
    private final int colspan = 1;

    @Builder.Default
    private final boolean isHeader = false;
}
