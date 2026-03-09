package com.dsi.rfp.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceItem {

    private String id;
    private String requirement;
    private String sourceClauseId;
    private int page;
    private boolean mandatory;
    private ComplianceChecklistStatus status;
}
