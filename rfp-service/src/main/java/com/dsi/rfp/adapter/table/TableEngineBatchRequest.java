package com.dsi.rfp.adapter.table;

import java.util.List;

record TableEngineBatchRequest(
    String documentBase64,
    List<Integer> pages
) {
}
