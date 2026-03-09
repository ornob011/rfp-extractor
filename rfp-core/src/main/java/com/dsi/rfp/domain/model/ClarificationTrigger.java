package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClarificationTrigger {

    private QuestionType type;
    private String clauseId;
    private int page;
    private String context;
    private String ruleId;
    private String entityField;
}
