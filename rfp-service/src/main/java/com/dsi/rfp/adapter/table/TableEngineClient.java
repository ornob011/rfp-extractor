package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.TableExtractionStrategy;

import java.util.List;

public interface TableEngineClient {

    List<TableEngineTable> extractTables(
        String documentPath,
        int pageNumber,
        TableExtractionStrategy strategy
    );
}
