package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class RulePackResults {

    String packId;

    @Builder.Default
    List<String> passedRules = List.of();

    @Builder.Default
    List<String> failedRules = List.of();

    @Builder.Default
    List<String> warnRules = List.of();

    Instant executedAt;
}
