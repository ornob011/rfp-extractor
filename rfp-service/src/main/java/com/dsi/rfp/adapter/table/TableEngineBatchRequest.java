package com.dsi.rfp.adapter.table;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record TableEngineBatchRequest(
    @JsonProperty("document_base64")
    String documentBase64,

    List<Integer> pages
) {
}
