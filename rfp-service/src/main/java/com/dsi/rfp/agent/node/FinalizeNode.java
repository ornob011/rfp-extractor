package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RfpDocument;
import com.dsi.rfp.domain.port.out.JobStatePort;
import com.dsi.rfp.domain.port.out.ResultPersistencePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FinalizeNode implements NodeAction<ExtractionState> {

    private final ResultPersistencePort resultPersistencePort;
    private final JobStatePort jobStatePort;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> apply(ExtractionState state) {
        RfpDocument document = assembleDocument(state);

        JsonNode resultJson = objectMapper.valueToTree(document);
        resultPersistencePort.saveResult(state.jobId(), resultJson);

        JsonNode sectionsJson = objectMapper.valueToTree(state.sections());
        jobStatePort.updateSectionsJson(state.jobId(), sectionsJson);

        log.info(
            "event=finalize.complete component=FinalizeNode jobId={} sections={} entities={}",
            state.jobId(),
            state.sections().size(),
            state.entities() != null
        );

        return Map.of();
    }

    private RfpDocument assembleDocument(ExtractionState state) {
        return RfpDocument.builder()
                          .jobId(state.jobId())
                          .sections(state.sections())
                          .entities(state.entities())
                          .tables(state.tables())
                          .confidenceMap(state.confidenceMap())
                          .clauses(state.clauses())
                          .build();
    }
}
