package com.dsi.rfp.adapter.table;

import java.util.List;
import java.util.Optional;

record ScannedTableResponse(
    List<String> headers,
    List<List<String>> rows
) {

    ScannedTableResponse {
        headers = Optional.ofNullable(headers).orElse(List.of());
        rows = Optional.ofNullable(rows).orElse(List.of());
    }
}
