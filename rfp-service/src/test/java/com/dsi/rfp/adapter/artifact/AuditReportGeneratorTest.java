package com.dsi.rfp.adapter.artifact;

import com.dsi.rfp.agent.EntityFieldReader;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.freemarker.FreeMarkerConfigurationFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditReportGeneratorTest {

    private AuditReportGenerator generator;

    @BeforeEach
    void setUp() throws Exception {
        ArtifactGenerationConfig config = new ArtifactGenerationConfig();
        EntityFieldReader entityFieldReader = new EntityFieldReader(new ObjectMapper());

        generator = new AuditReportGenerator(
            freeMarkerConfiguration(),
            config,
            new AuditReportModelFactory(
                config,
                entityFieldReader,
                new ArtifactDocumentContextResolver(config)
            ),
            new ObjectMapper()
        );
    }

    @Test
    void shouldReturnNonEmptyHtmlWhenDocumentProvided() {
        String html = generator.generate(
            docWithPages(5),
            stateWithRepairs(),
            resultsWithFindings()
        );

        assertThat(html).isNotEmpty();
        assertThat(html).contains("<html");
        assertThat(html).contains("Audit Report");
    }

    @Test
    void shouldIncludePageCountInStats() {
        String html = generator.generate(
            docWithPages(10),
            stateWithRepairs(),
            resultsWithFindings()
        );

        assertThat(html).contains("10");
    }

    @Test
    void shouldClassifyConfidenceCorrectly() {
        RfpDocument doc = RfpDocument.builder()
                                     .sections(List.of(Section.builder().title("Test").build()))
                                     .entities(RfpEntities.builder()
                                                          .clientName("Test Client")
                                                          .build())
                                     .pageClassifications(List.of(
                                         PageSummary.builder()
                                                    .pageNumber(1)
                                                    .classification(PageClassification.DIGITAL)
                                                    .build()
                                     ))
                                     .pageConfidences(Map.of(1, 0.3))
                                     .pageExtractionMethods(Map.of(
                                         1, PageExtractionMethod.TEXT_LAYER
                                     ))
                                     .confidenceMap(Map.of("clientName", 0.3))
                                     .build();

        String html = generator.generate(
            doc,
            stateWithRepairs(),
            resultsWithFindings()
        );

        assertThat(html).contains("conf-low");
    }

    private RfpDocument docWithPages(int count) {
        List<PageSummary> pages = java.util.stream.IntStream.rangeClosed(1, count)
                                                            .mapToObj(i -> PageSummary.builder()
                                                                                      .pageNumber(i)
                                                                                      .classification(PageClassification.DIGITAL)
                                                                                      .build())
                                                            .toList();

        return RfpDocument.builder()
                          .sections(List.of(
                              Section.builder().title("Test RFP").build()
                          ))
                          .entities(RfpEntities.builder()
                                               .clientName("Test Client")
                                               .build())
                          .pageClassifications(pages)
                          .pageConfidences(Map.of(1, 0.9))
                          .pageExtractionMethods(Map.of(
                              1, PageExtractionMethod.TEXT_LAYER
                          ))
                          .confidenceMap(Map.of("clientName", 0.95))
                          .build();
    }

    private ExtractionState stateWithRepairs() {
        ExtractionState state = mock(ExtractionState.class);
        when(state.repairLog()).thenReturn(List.of());
        return state;
    }

    private RulePackResults resultsWithFindings() {
        return RulePackResults.builder()
                              .packId("test-pack")
                              .findings(List.of(
                                  RuleFinding.builder()
                                             .ruleId("R1")
                                             .severity(RuleSeverity.FATAL)
                                             .status(RuleStatus.FAIL)
                                             .message("Missing title")
                                             .checkedAt(Instant.now())
                                             .build()
                              ))
                              .build();
    }

    private Configuration freeMarkerConfiguration() throws Exception {
        FreeMarkerConfigurationFactory factory = new FreeMarkerConfigurationFactory();
        factory.setTemplateLoaderPath("classpath:/templates/");
        factory.setDefaultEncoding("UTF-8");
        factory.setPreferFileSystemAccess(false);
        return factory.createConfiguration();
    }
}
