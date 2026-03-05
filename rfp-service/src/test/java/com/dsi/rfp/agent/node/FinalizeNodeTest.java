package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RfpEntities;
import com.dsi.rfp.domain.port.out.JobStatePort;
import com.dsi.rfp.domain.port.out.ResultPersistencePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FinalizeNodeTest {

    @Mock
    private ResultPersistencePort resultPersistencePort;

    @Mock
    private JobStatePort jobStatePort;

    private FinalizeNode node;

    @BeforeEach
    void setUp() {
        node = new FinalizeNode(
            resultPersistencePort,
            jobStatePort,
            new ObjectMapper()
        );
    }

    @Test
    void shouldPersistResultAndSections() {
        RfpEntities entities = RfpEntities.builder()
                                          .clientName("Test Corp")
                                          .build();

        Map<String, Object> data = ExtractionState.initial(42L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.ENTITIES.value(), entities);
        ExtractionState state = new ExtractionState(data);

        Map<String, Object> result = node.apply(state);

        assertThat(result).isEmpty();

        ArgumentCaptor<JsonNode> resultCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(resultPersistencePort).saveResult(eq(42L), resultCaptor.capture());

        JsonNode savedResult = resultCaptor.getValue();
        assertThat(savedResult.has("entities")).isTrue();
        assertThat(savedResult.has("sections")).isTrue();
        assertThat(savedResult.has("jobId")).isTrue();
        assertThat(savedResult.get("jobId").asLong()).isEqualTo(42L);
    }

    @Test
    void shouldUpdateSectionsJson() {
        Map<String, Object> data = ExtractionState.initial(42L, "/tmp/x.pdf");
        ExtractionState state = new ExtractionState(data);

        node.apply(state);

        verify(jobStatePort).updateSectionsJson(eq(42L), org.mockito.ArgumentMatchers.any(JsonNode.class));
    }
}
