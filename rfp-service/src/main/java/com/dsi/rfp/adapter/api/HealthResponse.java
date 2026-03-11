package com.dsi.rfp.adapter.api;

import com.dsi.rfp.domain.model.HealthStatus;
import com.dsi.rfp.domain.model.LlmProvider;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HealthResponse {
    private HealthStatus status;
    private LlmProvider provider;
    private String model;
}
