package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ExtractionState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractTablesNodeTest {

    private final ExtractTablesNode node = new ExtractTablesNode();

    @Test
    void shouldReturnEmptyTablesList() {
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        ExtractionState state = new ExtractionState(data);

        Map<String, Object> result = node.apply(state);

        assertThat(result).containsKey(ExtractionState.Key.TABLES.value());

        List<?> tables = (List<?>) result.get(ExtractionState.Key.TABLES.value());
        assertThat(tables).isEmpty();
    }
}
