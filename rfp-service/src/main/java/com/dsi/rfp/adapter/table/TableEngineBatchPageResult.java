package com.dsi.rfp.adapter.table;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record TableEngineBatchPageResult(
    @JsonProperty("page_number")
    int pageNumber,

    List<TableEngineTable> tables
) {
}
