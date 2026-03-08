package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.domain.model.RfpType;

import java.time.Instant;

public record RulePackSummaryDto(
    String packId,
    String version,
    RfpType rfpType,
    int ruleCount,
    Instant lastLoadedAt
) {
}
