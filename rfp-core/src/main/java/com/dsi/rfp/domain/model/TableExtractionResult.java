package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TableExtractionResult implements Serializable {

    @Builder.Default
    private UUID tableId = UUID.randomUUID();

    private UUID sectionId;
    private String clauseId;
    private int pageStart;
    private int pageEnd;
    private TableProvenance provenance;
    private String caption;
    private TableType type;

    @Builder.Default
    private List<String> headers = List.of();

    @Builder.Default
    private List<List<TableCell>> grid = List.of();

    private ExtractionConfidence confidence;
}
