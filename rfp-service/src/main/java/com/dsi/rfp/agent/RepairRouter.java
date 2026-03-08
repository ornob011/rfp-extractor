package com.dsi.rfp.agent;

import org.springframework.stereotype.Component;

@Component
public class RepairRouter {

    private final ConfidenceScoringConfig scoringConfig;

    public RepairRouter(ConfidenceScoringConfig scoringConfig) {
        this.scoringConfig = scoringConfig;
    }

    public boolean shouldRepair(ExtractionState state) {
        return !state.lowConfidenceQueue().isEmpty()
               && !state.repairExhausted()
               && state.totalRepairIterations() < scoringConfig.maxTotalRepairIterations();
    }
}
