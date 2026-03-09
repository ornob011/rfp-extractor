package com.dsi.rfp.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidClarityPack {

    private Long jobId;
    private Instant generatedAt;

    @Builder.Default
    private List<ClarificationQuestion> clarificationQuestions = List.of();

    @Builder.Default
    private List<AmbiguityItem> ambiguityItems = List.of();

    @Builder.Default
    private List<ComplianceItem> complianceItems = List.of();

    @Builder.Default
    private List<RiskItem> riskItems = List.of();
}
