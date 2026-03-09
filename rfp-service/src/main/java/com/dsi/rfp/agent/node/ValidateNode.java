package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ExtractionState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@Component
public class ValidateNode implements NodeAction<ExtractionState> {

    @Override
    public Map<String, Object> apply(ExtractionState state) {
        Path doc = Path.of(state.documentPath());

        if (!Files.exists(doc)) {
            throw new IllegalStateException(
                String.format("Document not found: %s", state.documentPath())
            );
        }

        log.info(
            "event=validate.ok component=ValidateNode jobId={} path={}",
            state.jobId(),
            state.documentPath()
        );

        return Map.of();
    }
}
