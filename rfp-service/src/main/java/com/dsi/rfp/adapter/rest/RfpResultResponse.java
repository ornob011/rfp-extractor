package com.dsi.rfp.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RfpResultResponse {

    private final Long jobId;
    private final JsonNode sections;
}
