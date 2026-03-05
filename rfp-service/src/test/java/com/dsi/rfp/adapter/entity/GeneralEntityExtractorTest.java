package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.adapter.extraction.DocumentChunkingService;
import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.agent.ExtractionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GeneralEntityExtractorTest {

    @Mock
    private LlmAdapter llmAdapter;

    private GeneralEntityExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new GeneralEntityExtractor(
            llmAdapter,
            new DocumentChunkingService(),
            new ObjectMapper(),
            new EntityExtractorMetadataRegistry()
        );
    }

    @Test
    void shouldReturnEmptyMapWhenNoChunks() {
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        ExtractionState state = new ExtractionState(data);

        Map<String, Object> result = extractor.extract(
            List.of(),
            List.of(),
            state
        );

        assertThat(result).isEmpty();
    }

    @Test
    void shouldResolveGeneralPromptKey() {
        assertThat(extractor.promptKey())
            .isEqualTo(PromptKey.GENERAL);
    }

    @Test
    void shouldLoadRequiredFieldsFromMetadata() {
        EntityExtractorMetadataRegistry metadataRegistry = new EntityExtractorMetadataRegistry();

        assertThat(metadataRegistry.requiredFields(PromptKey.GENERAL)).contains(
            "client_name",
            "submission_deadline"
        );
    }
}
