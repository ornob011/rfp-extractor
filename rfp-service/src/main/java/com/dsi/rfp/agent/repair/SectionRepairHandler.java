package com.dsi.rfp.agent.repair;

import com.dsi.rfp.adapter.extraction.LlmSectionSegmentFallback;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RepairStrategy;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SectionRepairHandler implements RepairHandler {

    private final LlmSectionSegmentFallback fallback;

    public SectionRepairHandler(LlmSectionSegmentFallback fallback) {
        this.fallback = fallback;
    }

    @Override
    public RepairStrategy strategy() {
        return RepairStrategy.RETRY_SECTION_SEGMENTATION;
    }

    @Override
    public Map<String, Object> repair(
        String componentId,
        ExtractionState state,
        int attemptNumber
    ) {
        return fallback.execute(state);
    }
}
