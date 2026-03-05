package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ExtractionState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunRulePackNodeTest {

    private final RunRulePackNode node = new RunRulePackNode();

    @Test
    void shouldReturnEmptyMap() {
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        ExtractionState state = new ExtractionState(data);

        Map<String, Object> result = node.apply(state);

        assertThat(result).isEmpty();
    }
}
