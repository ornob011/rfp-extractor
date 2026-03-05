package com.dsi.rfp.adapter.entity;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.Clause;
import com.dsi.rfp.domain.model.RfpEntities;
import com.dsi.rfp.domain.model.Section;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntityExtractor {

    private final GeneralEntityExtractor generalExtractor;
    private final SubmissionEntityExtractor submissionExtractor;
    private final FinancialEntityExtractor financialExtractor;
    private final IctEntityExtractor ictExtractor;
    private final StaffingEntityExtractor staffingExtractor;
    private final SupportEntityExtractor supportExtractor;
    private final EvaluationEntityExtractor evaluationExtractor;
    private final RfpEntitiesMapper entitiesMapper;

    public RfpEntities extractAll(
        List<Section> sections,
        List<Clause> clauses,
        ExtractionState state
    ) {
        log.info(
            "event=entity.extractAll.start component=EntityExtractor jobId={}",
            state.jobId()
        );

        Map<String, Object> merged = new HashMap<>();

        runExtractor(generalExtractor, sections, clauses, state, merged);
        runExtractor(submissionExtractor, sections, clauses, state, merged);
        runExtractor(financialExtractor, sections, clauses, state, merged);
        runExtractor(ictExtractor, sections, clauses, state, merged);
        runExtractor(staffingExtractor, sections, clauses, state, merged);
        runExtractor(supportExtractor, sections, clauses, state, merged);
        runExtractor(evaluationExtractor, sections, clauses, state, merged);

        log.info(
            "event=entity.extractAll.done component=EntityExtractor jobId={} fields={}",
            state.jobId(),
            merged.size()
        );

        return entitiesMapper.fromMap(merged);
    }

    private void runExtractor(
        BaseEntityExtractor extractor,
        List<Section> sections,
        List<Clause> clauses,
        ExtractionState state,
        Map<String, Object> target
    ) {
        target.putAll(
            extractor.extract(
                sections,
                clauses,
                state
            )
        );
    }
}
