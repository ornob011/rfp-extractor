package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.rulepack.RulePackExecutionService;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RfpEntities;
import com.dsi.rfp.domain.model.RulePackResults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunRulePackNodeTest {

    @Mock
    private RulePackExecutionService rulePackExecutionService;

    @InjectMocks
    private RunRulePackNode node;

    @Test
    void shouldStoreRulePackResultsFromExecutionService() {
        ExtractionState state = buildState();

        RulePackResults results = RulePackResults.builder()
                                                 .packId("bd-govt-ict-v1")
                                                 .packVersion("1.0.0")
                                                 .runTimestamp(Instant.now())
                                                 .findings(List.of())
                                                 .summary(Map.of())
                                                 .build();
        when(rulePackExecutionService.execute(any())).thenReturn(results);

        Map<String, Object> output = node.apply(state);

        assertThat(output).containsKey("rulePackResults");
        assertThat(output.get("rulePackResults")).isEqualTo(results);
    }

    private ExtractionState buildState() {
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/test.pdf");
        data.put("entities", RfpEntities.builder().clientName("Test").build());
        return new ExtractionState(data);
    }
}
