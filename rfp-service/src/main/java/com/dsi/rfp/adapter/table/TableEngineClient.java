package com.dsi.rfp.adapter.table;

import com.dsi.rfp.domain.model.TableExtractionStrategy;

import java.util.List;
import java.util.Map;

public interface TableEngineClient {

    List<TableEngineTable> extractTables(
        String documentPath,
        int pageNumber,
        TableExtractionStrategy strategy
    );

    Map<Integer, List<TableEngineTable>> extractTablesBatch(
        String documentPath,
        List<Integer> pageNumbers
    );
}
