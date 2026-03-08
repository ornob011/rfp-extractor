package com.dsi.rfp.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmJudgmentResult {

    private boolean finding;

    private String explanation;

    private double confidence;

    private RuleStatus status;
}
