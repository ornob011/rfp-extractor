package com.dsi.rfp.agent.repair;

import com.dsi.rfp.domain.model.RepairOutcome;
import org.springframework.stereotype.Component;

@Component
public class RepairOutcomeResolver {

    public RepairOutcome resolve(
        double before,
        double after,
        boolean exhausted
    ) {
        if (exhausted) {
            return RepairOutcome.MAX_RETRIES;
        }

        if (after > before) {
            return RepairOutcome.IMPROVED;
        }

        return RepairOutcome.NOT_IMPROVED;
    }
}
