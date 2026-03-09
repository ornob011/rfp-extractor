package com.dsi.rfp.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RiskItem {

    private String id;
    private String riskDescription;
    private String source;
    private RiskImpact impact;
    private String mitigationSuggestion;
    private String owner;
}
