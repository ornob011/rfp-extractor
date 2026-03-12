package com.dsi.rfp.agent.node;

import com.dsi.rfp.adapter.extraction.RepairDecisionTable;
import com.dsi.rfp.agent.ConfidenceScorer;
import com.dsi.rfp.agent.ConfidenceScoringConfig;
import com.dsi.rfp.agent.EntityFieldReader;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.agent.repair.RepairHandler;
import com.dsi.rfp.agent.repair.RepairOutcomeResolver;
import com.dsi.rfp.domain.model.*;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Component
public class RepairLoopNode implements NodeAction<ExtractionState> {

    private final RepairDecisionTable repairDecisionTable;
    private final ConfidenceScoringConfig scoringConfig;
    private final ConfidenceScorer confidenceScorer;
    private final EntityFieldReader entityFieldReader;
    private final Map<RepairStrategy, RepairHandler> handlers;
    private final RepairOutcomeResolver outcomeResolver;

    public RepairLoopNode(
        RepairDecisionTable repairDecisionTable,
        ConfidenceScoringConfig scoringConfig,
        ConfidenceScorer confidenceScorer,
        EntityFieldReader entityFieldReader,
        List<RepairHandler> handlers,
        RepairOutcomeResolver outcomeResolver
    ) {
        this.repairDecisionTable = repairDecisionTable;
        this.scoringConfig = scoringConfig;
        this.confidenceScorer = confidenceScorer;
        this.entityFieldReader = entityFieldReader;
        this.outcomeResolver = outcomeResolver;
        this.handlers = indexHandlers(handlers);
    }

    @Override
    public Map<String, Object> apply(ExtractionState state) throws Exception {
        List<String> queue = new ArrayList<>(state.lowConfidenceQueue());

        if (queue.isEmpty()) {
            return Map.of();
        }

        if (state.totalRepairIterations() >= scoringConfig.maxTotalRepairIterations()) {
            return Map.of(
                ExtractionState.Key.REPAIR_EXHAUSTED.value(),
                true
            );
        }

        String componentId = queue.removeFirst();
        RepairableComponent component = state.repairableComponents().get(componentId);

        log.info(
            "event=repair.dequeue component=RepairLoopNode componentId={} confidence={} queueSize={} iteration={}",
            componentId,
            state.confidenceMap().getOrDefault(componentId, 0.0),
            queue.size(),
            state.totalRepairIterations() + 1
        );

        if (component == null) {
            return missingComponentResult(
                state,
                componentId,
                queue
            );
        }

        return repairComponent(
            state,
            component,
            componentId,
            queue
        );
    }

    private Map<String, Object> repairComponent(
        ExtractionState state,
        RepairableComponent component,
        String componentId,
        List<String> queue
    ) throws Exception {
        int attemptNumber = countPreviousAttempts(
            state.repairLog(),
            componentId
        ) + 1;

        if (attemptNumber > scoringConfig.maxRetriesPerItem()) {
            return maxRetriesResult(
                state,
                component,
                componentId,
                attemptNumber,
                queue
            );
        }

        return executeRepair(
            state,
            component,
            componentId,
            attemptNumber,
            queue
        );
    }

    private Map<String, Object> executeRepair(
        ExtractionState state,
        RepairableComponent component,
        String componentId,
        int attemptNumber,
        List<String> queue
    ) throws Exception {
        double before = state.confidenceMap().getOrDefault(componentId, 0.0);
        RepairStrategy strategy = repairDecisionTable.strategyFor(
            component.getComponentType(),
            component.getConfidenceSource()
        );

        Map<String, Object> handlerUpdates = handlers.get(strategy)
                                                     .repair(
                                                         componentId,
                                                         state,
                                                         attemptNumber
                                                     );

        ExtractionState mergedState = mergeState(
            state,
            handlerUpdates
        );

        double after = scoreComponent(
            mergedState,
            componentId,
            component.getComponentType()
        );

        RepairOutcome outcome = outcomeResolver.resolve(
            before,
            after,
            false
        );

        return finalizeRepair(
            state,
            mergedState,
            componentId,
            component.getComponentType(),
            strategy,
            attemptNumber,
            before,
            after,
            outcome,
            queue
        );
    }

    private Map<String, Object> finalizeRepair(
        ExtractionState originalState,
        ExtractionState mergedState,
        String componentId,
        RepairComponentType componentType,
        RepairStrategy strategy,
        int attemptNumber,
        double before,
        double after,
        RepairOutcome outcome,
        List<String> queue
    ) {
        Map<String, Double> confidenceMap = new HashMap<>(mergedState.confidenceMap());
        confidenceMap.put(componentId, after);

        Map<String, RepairableComponent> repairables = new HashMap<>(mergedState.repairableComponents());
        repairableFor(
            mergedState,
            componentId,
            componentType
        ).ifPresent(repairable -> repairables.put(componentId, repairable));

        List<String> manualReview = new ArrayList<>(originalState.manualReviewRequired());

        queueFor(
            after,
            componentId,
            attemptNumber,
            queue,
            manualReview
        );

        List<RepairLogEntry> repairLog = new ArrayList<>(originalState.repairLog());
        repairLog.add(RepairLogEntry.builder()
                                    .componentId(componentId)
                                    .componentType(componentType)
                                    .attemptNumber(attemptNumber)
                                    .strategy(strategy)
                                    .beforeConfidence(before)
                                    .afterConfidence(after)
                                    .outcome(outcome)
                                    .reason(outcome.name())
                                    .timestamp(Instant.now())
                                    .build());

        Map<String, Object> updates = new HashMap<>(mergedState.data());

        updates.put(ExtractionState.Key.LOW_CONFIDENCE_QUEUE.value(), queue);
        updates.put(ExtractionState.Key.CONFIDENCE_MAP.value(), confidenceMap);
        updates.put(ExtractionState.Key.REPAIR_LOG.value(), repairLog);
        updates.put(ExtractionState.Key.REPAIRABLE_COMPONENTS.value(), repairables);
        updates.put(ExtractionState.Key.MANUAL_REVIEW_REQUIRED.value(), manualReview);
        updates.put(
            ExtractionState.Key.TOTAL_REPAIR_ITERATIONS.value(),
            originalState.totalRepairIterations() + 1
        );

        return updates;
    }

    private Map<String, Object> maxRetriesResult(
        ExtractionState state,
        RepairableComponent component,
        String componentId,
        int attemptNumber,
        List<String> queue
    ) {
        List<String> manualReview = new ArrayList<>(state.manualReviewRequired());
        manualReview.add(componentId);

        List<RepairLogEntry> repairLog = new ArrayList<>(state.repairLog());
        repairLog.add(RepairLogEntry.builder()
                                    .componentId(componentId)
                                    .componentType(component.getComponentType())
                                    .attemptNumber(attemptNumber)
                                    .strategy(RepairStrategy.NO_OP)
                                    .beforeConfidence(state.confidenceMap().getOrDefault(componentId, 0.0))
                                    .afterConfidence(state.confidenceMap().getOrDefault(componentId, 0.0))
                                    .outcome(RepairOutcome.MAX_RETRIES)
                                    .reason(RepairOutcome.MAX_RETRIES.name())
                                    .timestamp(Instant.now())
                                    .build());

        return Map.of(
            ExtractionState.Key.LOW_CONFIDENCE_QUEUE.value(), queue,
            ExtractionState.Key.MANUAL_REVIEW_REQUIRED.value(), manualReview,
            ExtractionState.Key.REPAIR_LOG.value(), repairLog,
            ExtractionState.Key.TOTAL_REPAIR_ITERATIONS.value(),
            state.totalRepairIterations() + 1
        );
    }

    private Map<String, Object> missingComponentResult(
        ExtractionState state,
        String componentId,
        List<String> queue
    ) {
        List<String> manualReview = new ArrayList<>(state.manualReviewRequired());
        manualReview.add(componentId);

        return Map.of(
            ExtractionState.Key.LOW_CONFIDENCE_QUEUE.value(), queue,
            ExtractionState.Key.MANUAL_REVIEW_REQUIRED.value(), manualReview,
            ExtractionState.Key.TOTAL_REPAIR_ITERATIONS.value(),
            state.totalRepairIterations() + 1
        );
    }

    private int countPreviousAttempts(
        List<RepairLogEntry> repairLog,
        String componentId
    ) {
        return (int) repairLog.stream()
                              .filter(entry -> entry.getComponentId().equals(componentId))
                              .count();
    }

    private ExtractionState mergeState(
        ExtractionState state,
        Map<String, Object> handlerUpdates
    ) {
        Map<String, Object> merged = new HashMap<>(state.data());
        merged.putAll(handlerUpdates);

        return new ExtractionState(merged);
    }

    private double scoreComponent(
        ExtractionState state,
        String componentId,
        RepairComponentType componentType
    ) {
        return switch (componentType) {
            case ENTITY -> entityFieldReader.readAllFields(state.entities())
                                            .entrySet()
                                            .stream()
                                            .filter(entry -> entry.getKey().equals(componentId))
                                            .map(Map.Entry::getValue)
                                            .mapToDouble(confidenceScorer::scoreValue)
                                            .findFirst()
                                            .orElse(0.0);
            case SECTION -> state.sections()
                                 .stream()
                                 .flatMap(section -> flattenSections(section).stream())
                                 .filter(section -> section.getId().toString().equals(componentId))
                                 .mapToDouble(confidenceScorer::scoreSection)
                                 .findFirst()
                                 .orElse(0.0);
            case TABLE -> state.tables()
                               .stream()
                               .filter(table -> table.getTableId().toString().equals(componentId))
                               .mapToDouble(confidenceScorer::scoreTable)
                               .findFirst()
                               .orElse(0.0);
        };
    }

    private Optional<RepairableComponent> repairableFor(
        ExtractionState state,
        String componentId,
        RepairComponentType componentType
    ) {
        return switch (componentType) {
            case ENTITY -> Optional.ofNullable(state.entities())
                                   .map(entityFieldReader::readAllFields)
                                   .map(fields -> fields.get(componentId))
                                   .map(value -> confidenceScorer.repairableEntity(
                                       componentId,
                                       value
                                   ));
            case SECTION -> state.sections()
                                 .stream()
                                 .flatMap(section -> flattenSections(section).stream())
                                 .filter(section -> section.getId().toString().equals(componentId))
                                 .findFirst()
                                 .map(confidenceScorer::repairableSection);
            case TABLE -> state.tables()
                               .stream()
                               .filter(table -> table.getTableId().toString().equals(componentId))
                               .findFirst()
                               .map(confidenceScorer::repairableTable);
        };
    }

    private List<Section> flattenSections(Section section) {
        return Stream.concat(
                         Stream.of(section),
                         section.getChildren()
                                .stream()
                                .flatMap(child -> flattenSections(child).stream())
                     )
                     .toList();
    }

    private void queueFor(
        double after,
        String componentId,
        int attemptNumber,
        List<String> queue,
        List<String> manualReview
    ) {
        if (after >= scoringConfig.lowConfidenceThreshold()) {
            return;
        }

        if (attemptNumber >= scoringConfig.maxRetriesPerItem()) {
            manualReview.add(componentId);
            return;
        }

        queue.add(componentId);
    }

    private Map<RepairStrategy, RepairHandler> indexHandlers(List<RepairHandler> repairHandlers) {
        EnumMap<RepairStrategy, RepairHandler> indexed = new EnumMap<>(RepairStrategy.class);
        repairHandlers.forEach(handler -> indexed.put(handler.strategy(), handler));

        return Map.copyOf(indexed);
    }
}
