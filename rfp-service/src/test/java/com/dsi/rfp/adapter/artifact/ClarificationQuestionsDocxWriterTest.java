package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClarificationQuestionsDocxWriterTest {

    private ClarificationQuestionsDocxWriter writer;

    @BeforeEach
    void setUp() throws Exception {
        ArtifactGenerationConfig config = new ArtifactGenerationConfig();
        Configuration freemarkerConfiguration = new Configuration(Configuration.VERSION_2_3_33);
        freemarkerConfiguration.setClassLoaderForTemplateLoading(
            Thread.currentThread().getContextClassLoader(),
            "/templates"
        );
        writer = new ClarificationQuestionsDocxWriter(
            freemarkerConfiguration,
            config,
            new ClarificationQuestionsModelFactory(
                new ArtifactDocumentContextResolver(config),
                config,
                Clock.systemDefaultZone()
            ),
            new ObjectMapper()
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
    void shouldRenderAllQuestionsAndReadableSources() throws Exception {
        List<ClarificationQuestion> questions = List.of(
            question("First question", "1.1", 1),
            question("Second question", null, 0),
            question("Third question", "2.2", 7)
        );

        byte[] bytes = writer.write(questions, minimalDoc());

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            List<String> paragraphs = document.getParagraphs()
                                              .stream()
                                              .map(paragraph -> paragraph.getText().trim())
                                              .filter(text -> !text.isEmpty())
                                              .toList();

            assertThat(paragraphs).contains("REQUEST FOR INFORMATION (RFI)");
            assertThat(paragraphs).contains("Q1: First question");
            assertThat(paragraphs).contains("Q2: Second question");
            assertThat(paragraphs).contains("Q3: Third question");
            assertThat(paragraphs).contains("Source: Section 1.1, Page 1");
            assertThat(paragraphs).contains("Source: Source unavailable");
            assertThat(paragraphs).contains("Source: Section 2.2, Page 7");
        }
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
