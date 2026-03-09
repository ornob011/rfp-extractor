package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.domain.model.RepairOutcome;
import com.dsi.rfp.domain.model.RepairStrategy;

public record RepairEventDto(
    String componentId,
    int attempt,
    RepairStrategy strategy,
    RepairOutcome result
) {
}
