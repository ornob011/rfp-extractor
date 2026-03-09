package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.ClarificationQuestion;
import com.dsi.rfp.domain.model.RfpDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
class ClarificationQuestionsModelFactory {

    private final ArtifactDocumentContextResolver contextResolver;
    private final Clock clock;

    ClarificationQuestionsTemplateModel.DocumentView create(
        List<ClarificationQuestion> questions,
        RfpDocument document
    ) {
        return new ClarificationQuestionsTemplateModel.DocumentView(
            contextResolver.title(document),
            contextResolver.procurementReference(document),
            LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE),
            questions
        );
    }
}
