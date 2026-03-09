package com.dsi.rfp.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmbiguityItem {

    private String id;
    private RuleSeverity severity;
    private AmbiguityCategory category;
    private String description;
    private String sourceClauseId;
    private int page;
    private String recommendedAction;
}
