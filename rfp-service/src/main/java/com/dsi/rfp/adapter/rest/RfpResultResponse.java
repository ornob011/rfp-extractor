package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.domain.model.RfpType;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RfpResultResponse {

    private final Long jobId;
    private final JsonNode sections;
    private final JsonNode entities;
    private final JsonNode confidenceMap;
    private final JsonNode tables;
    private final JsonNode pageDetails;
    private final JsonNode rulePackResults;
    private final RfpType rfpType;
    private final BadgeThresholds badgeThresholds;

    public record BadgeThresholds(
        double high,
        double medium
    ) {
    }
}
