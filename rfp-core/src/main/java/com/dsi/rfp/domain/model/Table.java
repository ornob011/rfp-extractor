package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class Table {

    String tableId;

    int pageNumber;

    String sectionId;

    @Builder.Default
    List<String> headers = List.of();

    @Builder.Default
    List<List<String>> rows = List.of();

    String tableType;
}
