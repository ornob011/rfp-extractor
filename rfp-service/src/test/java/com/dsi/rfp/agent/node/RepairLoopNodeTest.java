package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.extraction.RepairDecisionTable;
import com.dsi.rfp.adapter.extraction.RepairPolicyConfig;
import com.dsi.rfp.adapter.table.TableExtractionConfig;
import com.dsi.rfp.agent.ConfidenceScorer;
import com.dsi.rfp.agent.ConfidenceScoringConfig;
import com.dsi.rfp.agent.EntityFieldReader;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.agent.repair.RepairHandler;
import com.dsi.rfp.agent.repair.RepairOutcomeResolver;
import com.dsi.rfp.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RepairLoopNodeTest {

    @Test
    void shouldReturnEmptyMapWhenQueueIsEmpty() throws Exception {
        RepairLoopNode node = createNode(List.of(noOpHandler()));

        Map<String, Object> result = node.apply(new ExtractionState(ExtractionState.initial(1L, "/tmp/x.pdf")));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnRepairExhaustedWhenHardStopReached() throws Exception {
        RepairLoopNode node = createNode(List.of(noOpHandler()));
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.TOTAL_REPAIR_ITERATIONS.value(), 20);
        data.put(ExtractionState.Key.LOW_CONFIDENCE_QUEUE.value(), new ArrayList<>(List.of("comp-1")));

        Map<String, Object> result = node.apply(new ExtractionState(data));

        assertThat(result.get(ExtractionState.Key.REPAIR_EXHAUSTED.value())).isEqualTo(true);
    }

    @Test
    void shouldApplyNoOpAndAddToManualReviewWhenMaxRetriesExceeded() throws Exception {
        RepairLoopNode node = createNode(List.of(noOpHandler()));
        String componentId = "e-001";
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.LOW_CONFIDENCE_QUEUE.value(), new ArrayList<>(List.of(componentId)));
        data.put(ExtractionState.Key.REPAIRABLE_COMPONENTS.value(), Map.of(componentId, repairable(componentId)));
        data.put(ExtractionState.Key.REPAIR_LOG.value(), new ArrayList<>(List.of(
            buildEntry(componentId, 1),
            buildEntry(componentId, 2),
            buildEntry(componentId, 3)
        )));

        Map<String, Object> result = node.apply(new ExtractionState(data));

        assertThat(readStringList(result, ExtractionState.Key.MANUAL_REVIEW_REQUIRED)).contains(componentId);
        assertThat(readRepairLog(result).getLast().getOutcome()).isEqualTo(RepairOutcome.MAX_RETRIES);
    }

    @Test
    void shouldAddToManualReviewWhenComponentNotFound() throws Exception {
        RepairLoopNode node = createNode(List.of(noOpHandler()));
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.LOW_CONFIDENCE_QUEUE.value(), new ArrayList<>(List.of("unknown-comp")));

        Map<String, Object> result = node.apply(new ExtractionState(data));

        assertThat(readStringList(result, ExtractionState.Key.MANUAL_REVIEW_REQUIRED)).contains("unknown-comp");
    }

    @Test
    void shouldLeaveComponentInQueueWhenStillBelowThreshold() throws Exception {
        RepairLoopNode node = createNode(List.of(noOpHandler()));
        String componentId = "clientName";
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.LOW_CONFIDENCE_QUEUE.value(), new ArrayList<>(List.of(componentId)));
        data.put(ExtractionState.Key.CONFIDENCE_MAP.value(), Map.of(componentId, 0.4));
        data.put(ExtractionState.Key.REPAIRABLE_COMPONENTS.value(), Map.of(componentId, repairable(componentId)));
        data.put(ExtractionState.Key.ENTITIES.value(), RfpEntities.builder().clientName("AB").build());

        Map<String, Object> result = node.apply(new ExtractionState(data));

        assertThat(readStringList(result, ExtractionState.Key.LOW_CONFIDENCE_QUEUE)).contains(componentId);
    }

    @Test
    void shouldRemoveComponentFromQueueWhenConfidenceImproves() throws Exception {
        RepairHandler improveEntityHandler = new RepairHandler() {
            @Override
            public RepairStrategy strategy() {
                return RepairStrategy.WIDEN_ENTITY_CONTEXT;
            }

            @Override
            public Map<String, Object> repair(String componentId, ExtractionState state, int attemptNumber) {
                return Map.of(
                    ExtractionState.Key.ENTITIES.value(),
                    RfpEntities.builder().clientName("Government of Bangladesh").build()
                );
            }
        };

        RepairLoopNode node = createNode(List.of(improveEntityHandler));
        String componentId = "clientName";
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.LOW_CONFIDENCE_QUEUE.value(), new ArrayList<>(List.of(componentId)));
        data.put(ExtractionState.Key.CONFIDENCE_MAP.value(), Map.of(componentId, 0.4));
        data.put(ExtractionState.Key.REPAIRABLE_COMPONENTS.value(), Map.of(componentId, repairable(componentId)));
        data.put(ExtractionState.Key.ENTITIES.value(), RfpEntities.builder().clientName("AB").build());

        Map<String, Object> result = node.apply(new ExtractionState(data));

        assertThat(readStringList(result, ExtractionState.Key.LOW_CONFIDENCE_QUEUE)).doesNotContain(componentId);
        assertThat(readRepairLog(result).getFirst().getOutcome()).isEqualTo(RepairOutcome.IMPROVED);
    }

    @Test
    void shouldWriteRepairLogEntry() throws Exception {
        RepairLoopNode node = createNode(List.of(noOpHandler()));
        String componentId = "clientName";
        Map<String, Object> data = ExtractionState.initial(1L, "/tmp/x.pdf");
        data.put(ExtractionState.Key.LOW_CONFIDENCE_QUEUE.value(), new ArrayList<>(List.of(componentId)));
        data.put(ExtractionState.Key.REPAIRABLE_COMPONENTS.value(), Map.of(componentId, repairable(componentId)));
        data.put(ExtractionState.Key.ENTITIES.value(), RfpEntities.builder().clientName("AB").build());

        Map<String, Object> result = node.apply(new ExtractionState(data));

        assertThat(readRepairLog(result)).hasSize(1);
        assertThat(readRepairLog(result).getFirst().getStrategy()).isEqualTo(RepairStrategy.WIDEN_ENTITY_CONTEXT);
    }

    private RepairLoopNode createNode(List<RepairHandler> handlers) {
        ConfidenceScoringConfig config = new ConfidenceScoringConfig();
        EntityFieldReader fieldReader = new EntityFieldReader(new ObjectMapper());
        ConfidenceScorer scorer = new ConfidenceScorer(
            config,
            fieldReader,
            new TableExtractionConfig()
        );

        return new RepairLoopNode(
            new RepairDecisionTable(
                new RepairPolicyConfig()
            ),
            config,
            scorer,
            fieldReader,
            handlers,
            new RepairOutcomeResolver()
        );
    }

    private RepairHandler noOpHandler() {
        return new RepairHandler() {
            @Override
            public RepairStrategy strategy() {
                return RepairStrategy.WIDEN_ENTITY_CONTEXT;
            }

            @Override
            public Map<String, Object> repair(String componentId, ExtractionState state, int attemptNumber) {
                return Map.of();
            }
        };
    }

    private RepairableComponent repairable(String componentId) {
        return RepairableComponent.builder()
                                  .componentId(componentId)
                                  .componentType(RepairComponentType.ENTITY)
                                  .confidenceSource(ConfidenceSource.LLM)
                                  .currentConfidence(0.4)
                                  .build();
    }

    private RepairLogEntry buildEntry(String componentId, int attempt) {
        return RepairLogEntry.builder()
                             .componentId(componentId)
                             .componentType(RepairComponentType.ENTITY)
                             .attemptNumber(attempt)
                             .strategy(RepairStrategy.WIDEN_ENTITY_CONTEXT)
                             .beforeConfidence(0.4)
                             .afterConfidence(0.4)
                             .outcome(RepairOutcome.NOT_IMPROVED)
                             .reason("NOT_IMPROVED")
                             .timestamp(Instant.now())
                             .build();
    }

    private List<String> readStringList(Map<String, Object> result, ExtractionState.Key key) {
        return (List<String>) result.get(key.value());
    }

    private List<RepairLogEntry> readRepairLog(Map<String, Object> result) {
        return (List<RepairLogEntry>) result.get(ExtractionState.Key.REPAIR_LOG.value());
    }
}
