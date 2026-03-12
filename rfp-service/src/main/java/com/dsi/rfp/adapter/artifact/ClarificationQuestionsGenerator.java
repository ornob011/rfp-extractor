package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.adapter.llm.PromptTemplateRenderer;
import com.dsi.rfp.domain.exception.LlmUnavailableException;
import com.dsi.rfp.domain.model.ClarificationQuestion;
import com.dsi.rfp.domain.model.ClarificationTrigger;
import com.dsi.rfp.domain.model.RfpDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClarificationQuestionsGenerator {

    private static final JaroWinklerSimilarity QUESTION_SIMILARITY = new JaroWinklerSimilarity();
    private static final double DEDUPLICATION_THRESHOLD = 0.94;

    private final LlmAdapter llmAdapter;
    private final ArtifactGenerationConfig config;
    private final PromptTemplateRenderer promptTemplateRenderer;

    public List<ClarificationQuestion> generate(
        List<ClarificationTrigger> triggers,
        RfpDocument doc
    ) {
        String promptTemplate = config.clarificationQuestionPromptTemplate();

        List<ClarificationQuestion> questions = triggers.stream()
                                                        .map(trigger -> questionFromTrigger(trigger, doc, promptTemplate))
                                                        .toList();

        List<ClarificationQuestion> deduped = deduplicate(questions);

        List<ClarificationQuestion> result = deduped.stream()
                                                    .sorted(Comparator.comparingInt(ClarificationQuestion::getPriority))
                                                    .limit(config.maxQuestions())
                                                    .toList();

        log.info(
            "Generated {} clarification questions from {} triggers",
            result.size(),
            triggers.size()
        );

        return result;
    }

    private ClarificationQuestion questionFromTrigger(
        ClarificationTrigger trigger,
        RfpDocument doc,
        String promptTemplate
    ) {
        String prompt = promptTemplateRenderer.render(
            promptTemplate,
            Map.of(
                "triggerType", trigger.getType().name(),
                "context", trigger.getContext(),
                "clauseId", Optional.ofNullable(trigger.getClauseId()).orElse("N/A"),
                "page", String.valueOf(trigger.getPage())
            )
        );

        return llmAdapter.extractStructured(
                             config.systemPromptResource(),
                             prompt,
                             LlmQuestionResponse.class
                         )
                         .map(response -> buildQuestion(trigger, response))
                         .orElseThrow(() -> new LlmUnavailableException(
                             String.format(
                                 "Clarification question generation returned no structured response for trigger type: %s",
                                 trigger.getType()
                             )
                         ));
    }

    private ClarificationQuestion buildQuestion(
        ClarificationTrigger trigger,
        LlmQuestionResponse response
    ) {
        return ClarificationQuestion.builder()
                                    .id(UUID.randomUUID().toString())
                                    .questionText(response.questionText())
                                    .clauseId(trigger.getClauseId())
                                    .page(trigger.getPage())
                                    .questionType(trigger.getType())
                                    .priority(response.priority())
                                    .source(buildSource(trigger))
                                    .build();
    }

    private String buildSource(ClarificationTrigger trigger) {
        if (trigger.getRuleId() != null) {
            return String.format("rule:%s", trigger.getRuleId());
        }
        return String.format("entity:%s", trigger.getEntityField());
    }

    private List<ClarificationQuestion> deduplicate(
        List<ClarificationQuestion> questions
    ) {
        List<ClarificationQuestion> deduplicated = new ArrayList<>();

        questions.forEach(question -> mergeQuestion(
            deduplicated,
            question
        ));

        return deduplicated;
    }

    private ClarificationQuestion higherPriorityQuestion(
        ClarificationQuestion first,
        ClarificationQuestion second
    ) {
        if (first.getPriority() <= second.getPriority()) {
            return first;
        }

        return second;
    }

    private void mergeQuestion(
        List<ClarificationQuestion> deduplicated,
        ClarificationQuestion question
    ) {
        OptionalInt duplicateIndex = duplicateIndex(
            deduplicated,
            question
        );

        if (duplicateIndex.isEmpty()) {
            deduplicated.add(question);
            return;
        }

        deduplicated.set(
            duplicateIndex.getAsInt(),
            higherPriorityQuestion(
                deduplicated.get(duplicateIndex.getAsInt()),
                question
            )
        );
    }

    private OptionalInt duplicateIndex(
        List<ClarificationQuestion> deduplicated,
        ClarificationQuestion question
    ) {
        return java.util.stream.IntStream.range(
                                             0,
                                             deduplicated.size()
                                         )
                                         .filter(index -> isDuplicate(
                                             deduplicated.get(index),
                                             question
                                         ))
                                         .findFirst();
    }

    private boolean isDuplicate(
        ClarificationQuestion first,
        ClarificationQuestion second
    ) {
        if (first.getQuestionType() != second.getQuestionType()) {
            return false;
        }

        double similarity = QUESTION_SIMILARITY.apply(
            normalizeQuestionText(first.getQuestionText()),
            normalizeQuestionText(second.getQuestionText())
        );

        return similarity >= DEDUPLICATION_THRESHOLD;
    }

    private String normalizeQuestionText(
        String questionText
    ) {
        if (questionText == null) {
            return "";
        }

        return StringUtils.normalizeSpace(questionText)
                          .toLowerCase(Locale.ROOT);
    }

    record LlmQuestionResponse(String questionText, int priority) {
    }
}
