package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.ClarificationQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
class ClarificationDocxBlockFactory {

    private final ArtifactGenerationConfig config;

    List<ClarificationDocxBlock> create(
        ClarificationQuestionsTemplateModel.DocumentView view
    ) {
        return Stream.concat(
                         headerBlocks(view).stream(),
                         bodyBlocks(view.questions()).stream()
                     )
                     .toList();
    }

    private List<ClarificationDocxBlock> headerBlocks(
        ClarificationQuestionsTemplateModel.DocumentView view
    ) {
        return List.of(
            block(
                ClarificationDocxBlockType.TITLE,
                config.clarificationTitle()
            ),
            metaBlock(
                config.clarificationProjectLabel(),
                view.title()
            ),
            metaBlock(
                config.clarificationReferenceLabel(),
                view.procurementRef()
            ),
            metaBlock(
                config.clarificationDateLabel(),
                view.generatedDate()
            ),
            spacer()
        );
    }

    private List<ClarificationDocxBlock> bodyBlocks(
        List<ClarificationQuestion> questions
    ) {
        return Stream.concat(
                         questionBlocks(questions).stream(),
                         Stream.of(responseBlock())
                     )
                     .toList();
    }

    private List<ClarificationDocxBlock> questionBlocks(
        List<ClarificationQuestion> questions
    ) {
        return java.util.stream.IntStream.range(
                       0,
                       questions.size()
                   )
                                         .mapToObj(index -> blocksForQuestion(
                                             questions.get(index),
                                             index + 1
                                         ))
                                         .flatMap(List::stream)
                                         .toList();
    }

    private List<ClarificationDocxBlock> blocksForQuestion(
        ClarificationQuestion question,
        int number
    ) {
        return List.of(
            block(
                ClarificationDocxBlockType.QUESTION,
                String.format(
                    "Q%d: %s",
                    number,
                    question.getQuestionText()
                )
            ),
            block(
                ClarificationDocxBlockType.SOURCE,
                String.format(
                    "%s: [Section %s, Page %d]",
                    config.clarificationSourceLabel(),
                    sourceSection(question),
                    question.getPage()
                )
            ),
            spacer()
        );
    }

    private ClarificationDocxBlock responseBlock() {
        return block(
            ClarificationDocxBlockType.RESPONSE,
            config.clarificationClosingInstruction()
        );
    }

    private ClarificationDocxBlock metaBlock(
        String label,
        String value
    ) {
        return block(
            ClarificationDocxBlockType.META,
            String.format(
                "%s: %s",
                label,
                value
            )
        );
    }

    private String sourceSection(ClarificationQuestion question) {
        return java.util.Optional.ofNullable(question.getClauseId())
                                 .orElse(config.clarificationSourceSectionFallback());
    }

    private ClarificationDocxBlock spacer() {
        return block(
            ClarificationDocxBlockType.SPACER,
            ""
        );
    }

    private ClarificationDocxBlock block(
        ClarificationDocxBlockType type,
        String text
    ) {
        return new ClarificationDocxBlock(
            type,
            text
        );
    }
}
