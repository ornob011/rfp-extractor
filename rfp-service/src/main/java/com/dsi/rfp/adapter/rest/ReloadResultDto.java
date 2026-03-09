package com.dsi.rfp.adapter.rest;

import java.time.Instant;
import java.util.List;

public record ReloadResultDto(
    List<String> reloadedPacks,
    Instant timestamp
) {
}
