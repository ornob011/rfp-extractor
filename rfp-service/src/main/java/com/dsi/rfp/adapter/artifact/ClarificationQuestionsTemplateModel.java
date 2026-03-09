package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.ClarificationQuestion;

import java.util.List;

public final class ClarificationQuestionsTemplateModel {

    private ClarificationQuestionsTemplateModel() {
    }

    public record DocumentView(
        String title,
        String procurementRef,
        String generatedDate,
        List<ClarificationQuestion> questions
    ) {
    }
}
