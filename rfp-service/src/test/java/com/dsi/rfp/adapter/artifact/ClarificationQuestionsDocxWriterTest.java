package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClarificationQuestionsDocxWriterTest {

    private ClarificationQuestionsDocxWriter writer;

    @BeforeEach
    void setUp() throws Exception {
        ArtifactGenerationConfig config = new ArtifactGenerationConfig();
        writer = new ClarificationQuestionsDocxWriter(
            new ClarificationQuestionsModelFactory(
                new ArtifactDocumentContextResolver(config),
                Clock.systemDefaultZone()
            ),
            new ClarificationDocxBlockFactory(config)
        );
    }

    @Test
    void shouldReturnNonEmptyBytesWhenQuestionsProvided() {
        List<ClarificationQuestion> questions = List.of(
            question("Q1", "1.1", 1),
            question("Q2", "2.1", 3),
            question("Q3", "3.1", 5),
            question("Q4", "4.1", 7),
            question("Q5", "5.1", 10)
        );

        byte[] bytes = writer.write(questions, minimalDoc());

        assertThat(bytes).isNotEmpty();
        assertThat(bytes.length).isGreaterThan(100);
    }

    @Test
    void shouldReturnValidDocxWhenNoQuestionsProvided() {
        byte[] bytes = writer.write(List.of(), minimalDoc());

        assertThat(bytes).isNotEmpty();
    }

    private ClarificationQuestion question(
        String text,
        String clauseId,
        int page
    ) {
        return ClarificationQuestion.builder()
                                    .id("q-1")
                                    .questionText(text)
                                    .clauseId(clauseId)
                                    .page(page)
                                    .questionType(QuestionType.MANDATORY_CLARIFICATION)
                                    .priority(1)
                                    .source("rule:BD-ICT-001")
                                    .build();
    }

    private RfpDocument minimalDoc() {
        return RfpDocument.builder()
                          .sections(List.of(
                              Section.builder()
                                     .title("Test RFP")
                                     .build()
                          ))
                          .entities(RfpEntities.builder()
                                               .build())
                          .build();
    }
}
