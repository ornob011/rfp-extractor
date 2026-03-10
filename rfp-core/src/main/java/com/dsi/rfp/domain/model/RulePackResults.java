package com.dsi.rfp.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RulePackResults implements Serializable {

    private String packId;

    private String packVersion;

    private RfpType rfpType;

    private Instant runTimestamp;

    @Builder.Default
    private Map<RuleSeverity, Integer> summary = Map.of();

    @Builder.Default
    private List<RuleFinding> findings = List.of();
}
