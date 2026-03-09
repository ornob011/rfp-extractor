package com.dsi.rfp.agent;

import com.dsi.rfp.domain.model.RfpEntities;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionStateTest {

    @Test
    void shouldCreateInitialState() {
        Map<String, Object> data = ExtractionState.initial(42L, "/tmp/doc.pdf");
        ExtractionState state = new ExtractionState(data);

        assertThat(state.jobId()).isEqualTo(42L);
        assertThat(state.documentPath()).isEqualTo("/tmp/doc.pdf");
        assertThat(state.sections()).isEmpty();
        assertThat(state.tables()).isEmpty();
        assertThat(state.clauses()).isEmpty();
        assertThat(state.confidenceMap()).isEmpty();
    }

    @Test
    void shouldReturnDefaultsForMissingKeys() {
        ExtractionState state = new ExtractionState(Map.of());

        assertThat(state.jobId()).isEqualTo(0L);
        assertThat(state.documentPath()).isEmpty();
        assertThat(state.entities()).isNull();
        assertThat(state.totalRepairIterations()).isZero();
        assertThat(state.repairExhausted()).isFalse();
    }

    @Test
    void shouldReadEntitiesFromState() {
        RfpEntities entities = RfpEntities.builder()
                                          .clientName("Test Client")
                                          .build();

        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.ENTITIES.value(), entities);
        ExtractionState state = new ExtractionState(data);

        assertThat(state.entities()).isNotNull();
        assertThat(state.entities().getClientName()).isEqualTo("Test Client");
    }
}
