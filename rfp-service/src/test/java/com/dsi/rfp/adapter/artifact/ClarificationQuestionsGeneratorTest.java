package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.adapter.llm.PromptTemplateRenderer;
import com.dsi.rfp.domain.exception.LlmUnavailableException;
import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClarificationQuestionsGeneratorTest {

    @Mock
    private LlmAdapter llmAdapter;

    private ClarificationQuestionsGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ClarificationQuestionsGenerator(
            llmAdapter,
            new ArtifactGenerationConfig(),
            new PromptTemplateRenderer()
        );
    }

    @Test
    void shouldCallLlmOncePerTrigger() {
        List<ClarificationTrigger> triggers = List.of(
            triggerWithClause("ctx1", "rule1", "1.1"),
            triggerWithClause("ctx2", "rule2", "2.1"),
            triggerWithClause("ctx3", "rule3", "3.1")
        );

        when(llmAdapter.extractStructured(any(), any(), any()))
            .thenReturn(
                Optional.of(
                    new ClarificationQuestionsGenerator.LlmQuestionResponse(
                        "Please confirm the submission deadline.",
                        2
                    )
                ),
                Optional.of(
                    new ClarificationQuestionsGenerator.LlmQuestionResponse(
                        "Please clarify the evaluation methodology.",
                        2
                    )
                ),
                Optional.of(
                    new ClarificationQuestionsGenerator.LlmQuestionResponse(
                        "Please provide the expected project duration.",
                        2
                    )
                )
            );

        List<ClarificationQuestion> questions = generator.generate(
            triggers,
            minimalDoc()
        );

        assertThat(questions).hasSize(3);
        verify(llmAdapter, times(3)).extractStructured(
            any(),
            any(),
            eq(ClarificationQuestionsGenerator.LlmQuestionResponse.class)
        );
    }

    @Test
    void shouldDeduplicateQuestionsWithSameClauseAndType() {
        ClarificationTrigger t1 = ClarificationTrigger.builder()
                                                      .type(QuestionType.MANDATORY_CLARIFICATION)
                                                      .clauseId("1.1")
                                                      .ruleId("rule1")
                                                      .context("Missing field A")
                                                      .build();

        ClarificationTrigger t2 = ClarificationTrigger.builder()
                                                      .type(QuestionType.MANDATORY_CLARIFICATION)
                                                      .clauseId("1.1")
                                                      .ruleId("rule2")
                                                      .context("Missing field B")
                                                      .build();

        when(llmAdapter.extractStructured(any(), any(), any()))
            .thenReturn(Optional.of(
                new ClarificationQuestionsGenerator.LlmQuestionResponse(
                    "Question?",
                    1
                )
            ));

        List<ClarificationQuestion> questions = generator.generate(
            List.of(t1, t2),
            minimalDoc()
        );

        assertThat(questions).hasSize(1);
    }

    @Test
    void shouldKeepDistinctQuestionsWhenClauseIsMissing() {
        ClarificationTrigger t1 = ClarificationTrigger.builder()
                                                      .type(QuestionType.MANDATORY_CLARIFICATION)
                                                      .ruleId("rule1")
                                                      .context("Missing deadline")
                                                      .build();
        ClarificationTrigger t2 = ClarificationTrigger.builder()
                                                      .type(QuestionType.MANDATORY_CLARIFICATION)
                                                      .ruleId("rule2")
                                                      .context("Missing payment terms")
                                                      .build();

        when(llmAdapter.extractStructured(any(), any(), any()))
            .thenReturn(
                Optional.of(new ClarificationQuestionsGenerator.LlmQuestionResponse(
                    "Please confirm the bid submission deadline.",
                    1
                )),
                Optional.of(new ClarificationQuestionsGenerator.LlmQuestionResponse(
                    "Please clarify the payment terms and milestones.",
                    1
                ))
            );

        List<ClarificationQuestion> questions = generator.generate(
            List.of(t1, t2),
            minimalDoc()
        );

        assertThat(questions).hasSize(2);
    }

    @Test
    void shouldCapResultAtThirtyWhenManyTriggers() {
        List<ClarificationTrigger> triggers = IntStream.rangeClosed(1, 40)
                                                       .mapToObj(i -> ClarificationTrigger.builder()
                                                                                          .type(QuestionType.MANDATORY_CLARIFICATION)
                                                                                          .clauseId(String.format("clause-%d", i))
                                                                                          .ruleId(String.format("rule-%d", i))
                                                                                          .context(String.format("Context %d", i))
                                                                                          .build()
                                                       )
                                                       .toList();

        when(llmAdapter.extractStructured(any(), any(), any()))
            .thenReturn(Optional.of(
                new ClarificationQuestionsGenerator.LlmQuestionResponse(
                    "Question?",
                    3
                )
            ));

        List<ClarificationQuestion> questions = generator.generate(
            triggers,
            minimalDoc()
        );

        assertThat(questions).hasSizeLessThanOrEqualTo(30);
    }

    @Test
    void shouldPropagateWhenLlmFails() {
        List<ClarificationTrigger> triggers = List.of(
            trigger("Missing deadline", "rule1")
        );

        when(llmAdapter.extractStructured(any(), any(), any()))
            .thenThrow(new LlmUnavailableException("LLM down"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> generator.generate(
            triggers,
            minimalDoc()
        )).isInstanceOf(LlmUnavailableException.class);
    }

    private ClarificationTrigger trigger(String context, String ruleId) {
        return ClarificationTrigger.builder()
                                   .type(QuestionType.MANDATORY_CLARIFICATION)
                                   .ruleId(ruleId)
                                   .context(context)
                                   .build();
    }

    private ClarificationTrigger triggerWithClause(
        String context,
        String ruleId,
        String clauseId
    ) {
        return ClarificationTrigger.builder()
                                   .type(QuestionType.MANDATORY_CLARIFICATION)
                                   .ruleId(ruleId)
                                   .clauseId(clauseId)
                                   .context(context)
                                   .build();
    }

    private RfpDocument minimalDoc() {
        return RfpDocument.builder()
                          .sections(List.of())
                          .entities(RfpEntities.builder().build())
                          .build();
    }
}
