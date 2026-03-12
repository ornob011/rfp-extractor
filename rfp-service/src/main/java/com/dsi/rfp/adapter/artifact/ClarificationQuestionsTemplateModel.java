package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.ClarificationQuestion;

import java.util.List;

public final class ClarificationQuestionsTemplateModel {

    private ClarificationQuestionsTemplateModel() {
    }

    public record DocumentView(
        String title,
        String projectLabel,
        String titleValue,
        String referenceLabel,
        String procurementRef,
        String dateLabel,
        String generatedDate,
        String sourceLabel,
        List<QuestionView> questions,
        String closingInstruction
    ) {
    }

    public record QuestionView(
        int number,
        String questionText,
        String sourceText,
        ClarificationQuestion question
    ) {
    }
}
