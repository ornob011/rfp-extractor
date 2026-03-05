package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.entity.EntityExtractor;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RfpEntities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractEntitiesNode implements NodeAction<ExtractionState> {

    private final EntityExtractor entityExtractor;

    @Override
    public Map<String, Object> apply(ExtractionState state) {
        RfpEntities entities = entityExtractor.extractAll(
            state.sections(),
            state.clauses(),
            state
        );

        log.info(
            "event=entities.extracted component=ExtractEntitiesNode jobId={}",
            state.jobId()
        );

        return Map.of(ExtractionState.Key.ENTITIES.value(), entities);
    }
}
