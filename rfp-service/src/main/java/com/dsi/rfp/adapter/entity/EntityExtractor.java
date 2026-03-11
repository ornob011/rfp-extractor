package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.adapter.extraction.DocumentChunkingService;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.Clause;
import com.dsi.rfp.domain.model.DocumentChunk;
import com.dsi.rfp.domain.model.RfpEntities;
import com.dsi.rfp.domain.model.Section;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class EntityExtractor {

    private final List<BaseEntityExtractor> extractors;
    private final DocumentChunkingService chunkingService;
    private final RfpEntitiesMapper entitiesMapper;
    private final Executor llmTaskExecutor;

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
        @Qualifier("llmTaskExecutor") Executor llmTaskExecutor
    ) {
        this.extractors = List.of(
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
        this.llmTaskExecutor = llmTaskExecutor;
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

        List<CompletableFuture<Map<String, Object>>> futures =
            extractors.stream()
                      .map(extractor -> CompletableFuture.supplyAsync(
                          () -> extractor.extract(chunks, state),
                          llmTaskExecutor
                      ))
                      .toList();

        Map<String, Object> merged = new HashMap<>();

        futures.stream()
               .map(CompletableFuture::join)
               .forEach(merged::putAll);

        log.info(
            "event=entity.extractAll.done component=EntityExtractor jobId={} fields={}",
            state.jobId(),
            merged.size()
        );

        return entitiesMapper.fromMap(merged);
    }
}
