package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ConfidenceScorer;
import com.dsi.rfp.agent.ConfidenceScoringConfig;
import com.dsi.rfp.agent.EntityFieldReader;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RepairableComponent;
import com.dsi.rfp.domain.model.RfpEntities;
import com.dsi.rfp.domain.model.Section;
import com.dsi.rfp.domain.model.TableExtractionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreConfidenceNode implements NodeAction<ExtractionState> {

    private final ConfidenceScoringConfig scoringConfig;
    private final ConfidenceScorer confidenceScorer;
    private final EntityFieldReader entityFieldReader;

    @Override
    public Map<String, Object> apply(ExtractionState state) {
        Map<String, Double> scores = new LinkedHashMap<>();
        List<String> lowConfQueue = new ArrayList<>();
        List<String> manualReview = new ArrayList<>();
        Map<String, RepairableComponent> repairables = new LinkedHashMap<>();

        scoreEntities(
            state.entities(),
            scores,
            lowConfQueue,
            manualReview,
            repairables
        );

        scoreSections(
            state.sections(),
            scores,
            lowConfQueue,
            repairables
        );

        scoreTables(
            state.tables(),
            scores,
            lowConfQueue,
            repairables
        );

        scores.put(
            scoringConfig.completenessKey(),
            confidenceScorer.completeness(scores)
        );

        log.info(
            "event=confidence.scored component=ScoreConfidenceNode"
            + " jobId={} completeness={} lowConf={} manual={}",
            state.jobId(),
            scores.get(scoringConfig.completenessKey()),
            lowConfQueue.size(),
            manualReview.size()
        );

        return Map.of(
            ExtractionState.Key.CONFIDENCE_MAP.value(), scores,
            ExtractionState.Key.LOW_CONFIDENCE_QUEUE.value(), lowConfQueue,
            ExtractionState.Key.MANUAL_REVIEW_REQUIRED.value(), manualReview,
            ExtractionState.Key.REPAIRABLE_COMPONENTS.value(), repairables,
            ExtractionState.Key.REPAIR_EXHAUSTED.value(), false
        );
    }

    private void scoreEntities(
        RfpEntities entities,
        Map<String, Double> scores,
        List<String> queue,
        List<String> manualReview,
        Map<String, RepairableComponent> repairables
    ) {
        if (entities == null) {
            return;
        }

        entityFieldReader.readAllFields(entities)
                         .forEach((key, value) -> recordEntity(
                             key,
                             value,
                             scores,
                             queue,
                             manualReview,
                             repairables
                         ));
    }

    private void recordEntity(
        String key,
        Object value,
        Map<String, Double> scores,
        List<String> queue,
        List<String> manualReview,
        Map<String, RepairableComponent> repairables
    ) {
        double score = confidenceScorer.scoreValue(value);
        scores.put(key, score);
        repairables.put(
            key,
            confidenceScorer.repairableEntity(
                key,
                value
            )
        );
        enqueue(
            key,
            score,
            queue,
            manualReview
        );
    }

    private void scoreSections(
        List<Section> sections,
        Map<String, Double> scores,
        List<String> queue,
        Map<String, RepairableComponent> repairables
    ) {
        sections.forEach(section -> recordSection(
            section,
            scores,
            queue,
            repairables
        ));
    }

    private void recordSection(
        Section section,
        Map<String, Double> scores,
        List<String> queue,
        Map<String, RepairableComponent> repairables
    ) {
        String sectionId = section.getId().toString();
        double score = confidenceScorer.scoreSection(section);
        scores.put(sectionId, score);
        repairables.put(
            sectionId,
            confidenceScorer.repairableSection(section)
        );
        addLowConfidence(
            sectionId,
            score,
            queue
        );
    }

    private void scoreTables(
        List<TableExtractionResult> tables,
        Map<String, Double> scores,
        List<String> queue,
        Map<String, RepairableComponent> repairables
    ) {
        tables.forEach(table -> recordTable(
            table,
            scores,
            queue,
            repairables
        ));
    }

    private void recordTable(
        TableExtractionResult table,
        Map<String, Double> scores,
        List<String> queue,
        Map<String, RepairableComponent> repairables
    ) {
        String tableId = table.getTableId().toString();
        double score = confidenceScorer.scoreTable(table);
        scores.put(tableId, score);
        repairables.put(
            tableId,
            confidenceScorer.repairableTable(table)
        );
        addLowConfidence(
            tableId,
            score,
            queue
        );
    }

    private void enqueue(
        String key,
        double score,
        List<String> queue,
        List<String> manualReview
    ) {
        addLowConfidence(
            key,
            score,
            queue
        );

        if (scoringConfig.criticalFields().contains(key)
            && score < scoringConfig.manualReviewThreshold()) {
            manualReview.add(key);
        }
    }

    private void addLowConfidence(
        String componentId,
        double score,
        List<String> queue
    ) {
        if (score < scoringConfig.lowConfidenceThreshold()) {
            queue.add(componentId);
        }
    }
}
