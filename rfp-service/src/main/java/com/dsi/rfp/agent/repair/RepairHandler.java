package com.dsi.rfp.agent.repair;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RepairStrategy;

import java.util.Map;

public interface RepairHandler {

    RepairStrategy strategy();

    Map<String, Object> repair(
        String componentId,
        ExtractionState state,
        int attemptNumber
    ) throws Exception;
}
