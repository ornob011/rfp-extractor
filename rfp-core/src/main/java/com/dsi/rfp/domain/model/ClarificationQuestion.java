package com.dsi.rfp.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClarificationQuestion {

    private String id;
    private String questionText;
    private String clauseId;
    private int page;
    private QuestionType questionType;
    private int priority;
    private String source;
}
