package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.adapter.extraction.DocumentChunkingService;
import com.dsi.rfp.adapter.llm.LlmAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class SubmissionEntityExtractor extends BaseEntityExtractor {

    public SubmissionEntityExtractor(
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
        return PromptKey.SUBMISSION;
    }
}
