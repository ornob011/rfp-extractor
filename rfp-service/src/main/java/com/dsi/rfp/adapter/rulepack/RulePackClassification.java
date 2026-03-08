package com.dsi.rfp.adapter.rulepack;

import com.dsi.rfp.domain.model.RfpType;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record RulePackClassification(
    RfpType resolvedType,
    boolean ambiguous,
    double confidence,
    Map<String, Integer> packScores,
    List<String> candidatePackIds
) {
}
