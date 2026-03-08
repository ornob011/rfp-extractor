package com.dsi.rfp.agent.repair;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RepairStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NoOpRepairHandler implements RepairHandler {

    @Override
    public RepairStrategy strategy() {
        return RepairStrategy.NO_OP;
    }

    @Override
    public Map<String, Object> repair(
        String componentId,
        ExtractionState state,
        int attemptNumber
    ) {
        return Map.of();
    }
}
