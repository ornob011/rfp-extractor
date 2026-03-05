package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.application.service.PageClassificationService;
import com.dsi.rfp.domain.model.PageSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClassifyPagesNode implements NodeAction<ExtractionState> {

    private final PageClassificationService classificationService;

    @Override
    public Map<String, Object> apply(ExtractionState state) throws IOException {
        List<PageSummary> results = classificationService.classifyPages(
            state.jobId(),
            Path.of(state.documentPath())
        );

        log.info(
            "event=classify.complete component=ClassifyPagesNode jobId={} pages={}",
            state.jobId(),
            results.size()
        );

        return Map.of(ExtractionState.Key.PAGE_CLASSIFICATIONS.value(), results);
    }
}
