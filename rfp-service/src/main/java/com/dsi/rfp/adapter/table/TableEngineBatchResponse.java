package com.dsi.rfp.adapter.table;

import java.util.List;

record TableEngineBatchResponse(
    List<TableEngineBatchPageResult> results
) {
}
