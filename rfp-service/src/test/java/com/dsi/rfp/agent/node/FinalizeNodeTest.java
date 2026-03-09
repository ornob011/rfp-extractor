package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.application.service.ArtifactApplicationService;
import com.dsi.rfp.domain.model.RfpEntities;
import com.dsi.rfp.domain.model.RulePackResults;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FinalizeNodeTest {

    @Mock
    private ResultPersistencePort resultPersistencePort;

    @Mock
    private JobStatePort jobStatePort;

    @Mock
    private ArtifactApplicationService artifactApplicationService;

    private FinalizeNode node;

    @BeforeEach
    void setUp() {
        node = new FinalizeNode(
            resultPersistencePort,
            jobStatePort,
            artifactApplicationService,
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
        data.put(
            ExtractionState.Key.RULE_PACK_RESULTS.value(),
            RulePackResults.builder()
                           .packId("bd-govt-ict-v1")
                           .findings(java.util.List.of())
                           .build()
        );
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
        data.put(
            ExtractionState.Key.RULE_PACK_RESULTS.value(),
            RulePackResults.builder()
                           .packId("bd-govt-ict-v1")
                           .findings(java.util.List.of())
                           .build()
        );
        ExtractionState state = new ExtractionState(data);

        node.apply(state);

        verify(jobStatePort).updateSectionsJson(eq(42L), org.mockito.ArgumentMatchers.any(JsonNode.class));
    }

    @Test
    void shouldRequireRulePackResultsBeforeArtifactGeneration() {
        Map<String, Object> data = ExtractionState.initial(42L, "/tmp/x.pdf");
        ExtractionState state = new ExtractionState(data);

        assertThatThrownBy(() -> node.apply(state))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Rule pack results are required");
    }

    @Test
    void shouldGenerateArtifactsWhenRulePackResultsExist() {
        Map<String, Object> data = ExtractionState.initial(42L, "/tmp/x.pdf");
        data.put(
            ExtractionState.Key.RULE_PACK_RESULTS.value(),
            RulePackResults.builder()
                           .packId("bd-govt-ict-v1")
                           .findings(java.util.List.of())
                           .build()
        );
        ExtractionState state = new ExtractionState(data);

        node.apply(state);

        verify(artifactApplicationService).generateAll(
            eq(42L),
            org.mockito.ArgumentMatchers.any(),
            eq(state),
            org.mockito.ArgumentMatchers.any()
        );
    }
}
