package com.dsi.rfp.domain.port.out;

import com.dsi.rfp.domain.model.TableEngineTable;
import com.dsi.rfp.domain.model.TableExtractionStrategy;

import java.util.List;
import java.util.Map;

public interface TableEnginePort {

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
