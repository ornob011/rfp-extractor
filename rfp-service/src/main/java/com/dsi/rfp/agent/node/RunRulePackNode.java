package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.rulepack.RulePackExecutionService;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RfpDocument;
import com.dsi.rfp.domain.model.RulePackResults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunRulePackNode implements NodeAction<ExtractionState> {

    private final RulePackExecutionService rulePackExecutionService;

    @Override
    public Map<String, Object> apply(ExtractionState state) {
        RulePackResults results = rulePackExecutionService.execute(
            assembleDocument(state)
        );

        log.info(
            "event=rulepack.complete component=RunRulePackNode jobId={} packId={} findings={}",
            state.jobId(),
            results.getPackId(),
            results.getFindings().size()
        );

        return Map.of(
            ExtractionState.Key.RULE_PACK_RESULTS.value(),
            results
        );
    }

    private RfpDocument assembleDocument(ExtractionState state) {
        return RfpDocument.builder()
                          .jobId(state.jobId())
                          .sections(state.sections())
                          .entities(state.entities())
                          .tables(state.tables())
                          .confidenceMap(state.confidenceMap())
                          .clauses(state.clauses())
                          .pageClassifications(state.pageClassifications())
                          .pageConfidences(state.pageConfidences())
                          .pageExtractionMethods(state.pageExtractionMethods())
                          .build();
    }
}
