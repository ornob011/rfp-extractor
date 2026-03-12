package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.ClarificationQuestion;
import com.dsi.rfp.domain.model.RfpDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
class ClarificationQuestionsModelFactory {

    private final ArtifactDocumentContextResolver contextResolver;
    private final ArtifactGenerationConfig config;
    private final Clock clock;

    ClarificationQuestionsTemplateModel.DocumentView create(
        List<ClarificationQuestion> questions,
        RfpDocument document
    ) {
        return new ClarificationQuestionsTemplateModel.DocumentView(
            config.clarificationTitle(),
            config.clarificationProjectLabel(),
            contextResolver.title(document),
            config.clarificationReferenceLabel(),
            contextResolver.procurementReference(document),
            config.clarificationDateLabel(),
            LocalDate.now(clock).format(DateTimeFormatter.ISO_LOCAL_DATE),
            config.clarificationSourceLabel(),
            questionViews(questions),
            config.clarificationClosingInstruction()
        );
    }

    private List<ClarificationQuestionsTemplateModel.QuestionView> questionViews(
        List<ClarificationQuestion> questions
    ) {
        return IntStream.range(
                            0,
                            questions.size()
                        )
                        .mapToObj(index -> questionView(
                            questions.get(index),
                            index + 1
                        ))
                        .toList();
    }

    private ClarificationQuestionsTemplateModel.QuestionView questionView(
        ClarificationQuestion question,
        int number
    ) {
        return new ClarificationQuestionsTemplateModel.QuestionView(
            number,
            question.getQuestionText(),
            sourceText(question),
            question
        );
    }

    private String sourceText(
        ClarificationQuestion question
    ) {
        String section = Optional.ofNullable(question.getClauseId())
                                 .filter(value -> !value.isBlank())
                                 .map(value -> String.format("Section %s", value))
                                 .orElse(null);
        String page = Optional.of(question.getPage())
                              .filter(value -> value > 0)
                              .map(value -> String.format("Page %d", value))
                              .orElse(null);

        return joinSource(
            section,
            page
        );
    }

    private String joinSource(
        String section,
        String page
    ) {
        if (section == null && page == null) {
            return config.clarificationSourceUnavailableLabel();
        }

        if (section == null) {
            return page;
        }

        if (page == null) {
            return section;
        }

        return String.format(
            "%s, %s",
            section,
            page
        );
    }
}
