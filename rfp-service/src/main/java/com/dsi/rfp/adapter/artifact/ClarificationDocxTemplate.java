package com.dsi.rfp.adapter.artifact;

import java.util.List;

final class ClarificationDocxTemplate {

    private ClarificationDocxTemplate() {
    }

    record Document(
        String title,
        List<MetadataLine> metadata,
        List<QuestionLine> questions,
        String closingInstruction
    ) {
    }

    record MetadataLine(
        String label,
        String value
    ) {
    }

    record QuestionLine(
        String label,
        String text,
        String sourceLabel,
        String sourceText
    ) {
    }
}
