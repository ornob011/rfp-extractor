package com.dsi.rfp.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class RulePackDefinition {

    @JsonProperty("pack_id")
    private String packId;

    @JsonProperty("pack_version")
    private String packVersion;

    @JsonProperty("rfp_type")
    private RfpType rfpType;

    private List<RuleDefinition> rules;

    private Instant loadedAt;
}
