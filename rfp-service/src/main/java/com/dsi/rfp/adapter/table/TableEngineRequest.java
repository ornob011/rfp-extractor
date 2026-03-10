package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.TableExtractionStrategy;

record TableEngineRequest(
    String documentPath,
    String documentBase64,
    int pageNumber,
    TableExtractionStrategy strategy
) {
}
