package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.adapter.extraction.DocumentChunkingService;
import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class GeneralEntityExtractor extends BaseEntityExtractor {

    public GeneralEntityExtractor(
        LlmAdapter llmAdapter,
        DocumentChunkingService chunkingService,
        ObjectMapper objectMapper,
        EntityExtractorMetadataRegistry metadataRegistry
    ) {
        super(
            llmAdapter,
            chunkingService,
            objectMapper,
            metadataRegistry
        );
    }

    @Override
    protected PromptKey promptKey() {
        return PromptKey.GENERAL;
    }
}
