package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.adapter.extraction.DocumentChunkingService;
import com.dsi.rfp.adapter.extraction.DocumentEvidenceIndex;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.Clause;
import com.dsi.rfp.domain.model.DocumentChunk;
import com.dsi.rfp.domain.model.RfpEntities;
import com.dsi.rfp.domain.model.Section;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EntityExtractor {

    private final Map<PromptKey, BaseEntityExtractor> extractors;
    private final DocumentChunkingService chunkingService;
    private final RfpEntitiesMapper entitiesMapper;
    private final DocumentEvidenceIndex evidenceIndex;
    private final EntityRetrievalConfig retrievalConfig;
    private final EntityExtractorMetadataRegistry metadataRegistry;

    public EntityExtractor(
        GeneralEntityExtractor generalExtractor,
        SubmissionEntityExtractor submissionExtractor,
        FinancialEntityExtractor financialExtractor,
        IctEntityExtractor ictExtractor,
        StaffingEntityExtractor staffingExtractor,
        SupportEntityExtractor supportExtractor,
        EvaluationEntityExtractor evaluationExtractor,
        DocumentChunkingService chunkingService,
        RfpEntitiesMapper entitiesMapper,
        DocumentEvidenceIndex evidenceIndex,
        EntityRetrievalConfig retrievalConfig,
        EntityExtractorMetadataRegistry metadataRegistry
    ) {
        this.extractors = extractorMap(
            generalExtractor,
            submissionExtractor,
            financialExtractor,
            ictExtractor,
            staffingExtractor,
            supportExtractor,
            evaluationExtractor
        );
        this.chunkingService = chunkingService;
        this.entitiesMapper = entitiesMapper;
        this.evidenceIndex = evidenceIndex;
        this.retrievalConfig = retrievalConfig;
        this.metadataRegistry = metadataRegistry;
    }

    public RfpEntities extractAll(
        List<Section> sections,
        List<Clause> clauses,
        ExtractionState state
    ) {
        log.info(
            "event=entity.extractAll.start component=EntityExtractor jobId={}",
            state.jobId()
        );

        List<DocumentChunk> chunks = chunkingService.chunkDocument(
            sections,
            clauses
        );

        Map<String, Object> merged = new HashMap<>();

        retrievalConfig.extractionOrder()
                       .forEach(promptKey -> mergeDomain(
                           merged,
                           promptKey,
                           chunks,
                           state
                       ));

        log.info(
            "event=entity.extractAll.done component=EntityExtractor jobId={} fields={}",
            state.jobId(),
            merged.size()
        );

        return entitiesMapper.fromMap(merged);
    }

    public RfpEntities extractTargeted(
        String fieldName,
        List<Section> sections,
        List<Clause> clauses,
        ExtractionState state
    ) {
        PromptKey promptKey = metadataRegistry.promptKeyForField(fieldName);

        if (promptKey == null) {
            return extractAll(
                sections,
                clauses,
                state
            );
        }

        List<DocumentChunk> chunks = chunkingService.chunkDocument(
            sections,
            clauses
        );

        Map<String, Object> merged = new HashMap<>();

        mergeDomain(
            merged,
            promptKey,
            chunks,
            state
        );

        return entitiesMapper.fromMap(merged);
    }

    private Map<PromptKey, BaseEntityExtractor> extractorMap(
        BaseEntityExtractor... extractors
    ) {
        Map<PromptKey, BaseEntityExtractor> mapping = new EnumMap<>(PromptKey.class);

        for (BaseEntityExtractor extractor : extractors) {
            mapping.put(
                extractor.key(),
                extractor
            );
        }

        return Map.copyOf(mapping);
    }

    private void mergeDomain(
        Map<String, Object> merged,
        PromptKey promptKey,
        List<DocumentChunk> chunks,
        ExtractionState state
    ) {
        EntityRetrievalConfig.DomainConfig domainConfig = retrievalConfig.domain(promptKey);
        DocumentEvidenceIndex.RetrievalResult<DocumentChunk> retrieval = evidenceIndex.retrieveChunks(
            chunks,
            domainConfig.queries(),
            domainConfig.topK(),
            domainConfig.minimumScore()
        );

        if (!domainConfig.alwaysRun() && retrieval.items().isEmpty()) {
            log.info(
                "event=entity.domain.skip component=EntityExtractor jobId={} domain={} bestScore={}",
                state.jobId(),
                promptKey,
                retrieval.bestScore()
            );

            return;
        }

        List<DocumentChunk> evidenceChunks = evidenceChunks(
            chunks,
            retrieval,
            domainConfig
        );

        merged.putAll(
            extractors.get(promptKey).extract(
                evidenceChunks,
                state
            )
        );
    }

    private List<DocumentChunk> evidenceChunks(
        List<DocumentChunk> chunks,
        DocumentEvidenceIndex.RetrievalResult<DocumentChunk> retrieval,
        EntityRetrievalConfig.DomainConfig domainConfig
    ) {
        if (!retrieval.items().isEmpty()) {
            return retrieval.items();
        }

        if (domainConfig.alwaysRun() && !retrieval.rankedItems().isEmpty()) {
            return retrieval.rankedItems();
        }

        return chunks.stream()
                     .limit(domainConfig.topK())
                     .toList();
    }
}
