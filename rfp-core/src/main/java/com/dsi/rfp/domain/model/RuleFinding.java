package com.dsi.rfp.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleFinding implements Serializable {

    private String ruleId;

    private RuleSeverity severity;

    private RuleStatus status;

    private String message;

    private String evidence;

    private Instant checkedAt;
}
