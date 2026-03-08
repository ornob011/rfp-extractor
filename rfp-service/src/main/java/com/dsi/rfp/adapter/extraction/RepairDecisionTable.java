package com.dsi.rfp.adapter.extraction;

import com.dsi.rfp.domain.model.ConfidenceSource;
import com.dsi.rfp.domain.model.RepairComponentType;
import com.dsi.rfp.domain.model.RepairStrategy;
import org.springframework.stereotype.Component;

@Component
public class RepairDecisionTable {

    private final RepairPolicyConfig repairPolicyConfig;

    public RepairDecisionTable(
        RepairPolicyConfig repairPolicyConfig
    ) {
        this.repairPolicyConfig = repairPolicyConfig;
    }

    public RepairStrategy strategyFor(
        RepairComponentType componentType,
        ConfidenceSource confidenceSource
    ) {
        return repairPolicyConfig.strategyFor(
            componentType,
            confidenceSource
        );
    }
}
