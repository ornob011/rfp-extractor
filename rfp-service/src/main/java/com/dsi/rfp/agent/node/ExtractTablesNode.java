package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ExtractionState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ExtractTablesNode implements NodeAction<ExtractionState> {

    @Override
    public Map<String, Object> apply(ExtractionState state) {
        log.info(
            "event=tables.stub component=ExtractTablesNode jobId={} — stub (Sprint 5)",
            state.jobId()
        );

        return Map.of(ExtractionState.Key.TABLES.value(), List.of());
    }
}
