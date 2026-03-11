package com.dsi.rfp.agent.repair;

import com.dsi.rfp.adapter.entity.EntityExtractor;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RepairStrategy;
import com.dsi.rfp.domain.model.RfpEntities;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class EntityRepairHandler implements RepairHandler {

    private final EntityExtractor entityExtractor;

    public EntityRepairHandler(EntityExtractor entityExtractor) {
        this.entityExtractor = entityExtractor;
    }

    @Override
    public RepairStrategy strategy() {
        return RepairStrategy.WIDEN_ENTITY_CONTEXT;
    }

    @Override
    public Map<String, Object> repair(
        String componentId,
        ExtractionState state,
        int attemptNumber
    ) {
        RfpEntities updated = entityExtractor.extractTargeted(
            componentId,
            state.sections(),
            state.clauses(),
            state
        );

        return Map.of(
            ExtractionState.Key.ENTITIES.value(),
            updated
        );
    }
}
