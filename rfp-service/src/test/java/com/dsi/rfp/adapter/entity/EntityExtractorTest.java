package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.adapter.extraction.DocumentChunkingService;
import com.dsi.rfp.adapter.extraction.DocumentEvidenceIndex;
import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.config.YamlConfigLoader;
import com.dsi.rfp.domain.model.RfpEntities;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityExtractorTest {

    @Mock
    private LlmAdapter llmAdapter;

    @Mock
    private DocumentChunkingService chunkingService;

    private EntityExtractor extractor;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        RfpEntitiesMapper mapper = new RfpEntitiesMapper(new ObjectMapper(), new RfpEntitiesMapperConfigRegistry());
        EntityExtractorMetadataRegistry metadataRegistry = new EntityExtractorMetadataRegistry();
        DocumentEvidenceIndex evidenceIndex = new DocumentEvidenceIndex();
        EntityRetrievalConfig retrievalConfig = new EntityRetrievalConfig(new YamlConfigLoader());

        GeneralEntityExtractor general = new GeneralEntityExtractor(
            llmAdapter,
            chunkingService,
            objectMapper,
            metadataRegistry
        );
        SubmissionEntityExtractor submission = new SubmissionEntityExtractor(
            llmAdapter,
            chunkingService,
            objectMapper,
            metadataRegistry
        );
        FinancialEntityExtractor financial = new FinancialEntityExtractor(
            llmAdapter,
            chunkingService,
            objectMapper,
            metadataRegistry
        );
        IctEntityExtractor ict = new IctEntityExtractor(
            llmAdapter,
            chunkingService,
            objectMapper,
            metadataRegistry
        );
        StaffingEntityExtractor staffing = new StaffingEntityExtractor(
            llmAdapter,
            chunkingService,
            objectMapper,
            metadataRegistry
        );
        SupportEntityExtractor support = new SupportEntityExtractor(
            llmAdapter,
            chunkingService,
            objectMapper,
            metadataRegistry
        );
        EvaluationEntityExtractor evaluation = new EvaluationEntityExtractor(
            llmAdapter,
            chunkingService,
            objectMapper,
            metadataRegistry
        );

        extractor = new EntityExtractor(
            general,
            submission,
            financial,
            ict,
            staffing,
            support,
            evaluation,
            chunkingService,
            mapper,
            evidenceIndex,
            retrievalConfig,
            metadataRegistry
        );
    }

    @Test
    void shouldReturnEmptyEntitiesWhenNoChunks() {
        when(chunkingService.chunkDocument(List.of(), List.of()))
            .thenReturn(List.of());

        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        ExtractionState state = new ExtractionState(data);

        RfpEntities result = extractor.extractAll(
            List.of(),
            List.of(),
            state
        );

        assertThat(result).isNotNull();
        assertThat(result.getClientName()).isNull();
    }
}
