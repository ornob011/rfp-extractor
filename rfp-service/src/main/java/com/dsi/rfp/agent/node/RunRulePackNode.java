package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ExtractionState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class RunRulePackNode implements NodeAction<ExtractionState> {

    @Override
    public Map<String, Object> apply(ExtractionState state) {
        log.info(
            "event=rulepack.stub component=RunRulePackNode jobId={} — skipped (Sprint 8)",
            state.jobId()
        );

        return Map.of();
    }
}
