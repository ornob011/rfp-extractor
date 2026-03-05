package com.dsi.rfp.agent.node;

import com.dsi.rfp.agent.ConfidenceScoringConfig;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.RfpEntities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreConfidenceNode implements NodeAction<ExtractionState> {

    private final ConfidenceScoringConfig scoringConfig;

    @Override
    public Map<String, Object> apply(ExtractionState state) {
        if (state.entities() == null) {
            return buildEmptyResult();
        }

        return scoreEntities(state);
    }

    private Map<String, Object> buildEmptyResult() {
        return Map.of(
            ExtractionState.Key.CONFIDENCE_MAP.value(),
            Map.of(scoringConfig.completenessKey(), 0.0),
            ExtractionState.Key.LOW_CONFIDENCE_QUEUE.value(),
            List.of()
        );
    }

    private Map<String, Object> scoreEntities(ExtractionState state) {
        Map<String, Double> scores = new LinkedHashMap<>();
        List<String> lowConfQueue = new ArrayList<>();

        scoreAllFields(
            state.entities(),
            scores,
            lowConfQueue
        );

        scores.put(
            scoringConfig.completenessKey(),
            computeCompleteness(scores)
        );

        log.info(
            "event=confidence.scored component=ScoreConfidenceNode jobId={} completeness={} lowConfFields={}",
            state.jobId(),
            scores.get(scoringConfig.completenessKey()),
            lowConfQueue.size()
        );

        return Map.of(
            ExtractionState.Key.CONFIDENCE_MAP.value(),
            scores,
            ExtractionState.Key.LOW_CONFIDENCE_QUEUE.value(),
            lowConfQueue
        );
    }

    private void scoreAllFields(
        RfpEntities entities,
        Map<String, Double> scores,
        List<String> queue
    ) {
        readAllEntityFields(entities).forEach((key, value) ->
            scoreField(scores, queue, key, value)
        );
    }

    private Map<String, Object> readAllEntityFields(RfpEntities entities) {
        try {
            PropertyDescriptor[] descriptors = Introspector.getBeanInfo(
                RfpEntities.class,
                Object.class
            ).getPropertyDescriptors();

            Map<String, Object> fields = new LinkedHashMap<>();

            Arrays.stream(descriptors)
                  .forEach(descriptor -> fields.put(
                      descriptor.getName(),
                      invokeReader(entities, descriptor)
                  ));

            return fields;
        } catch (IntrospectionException exception) {
            throw new IllegalStateException("Failed to inspect RfpEntities properties", exception);
        }
    }

    private Object invokeReader(
        RfpEntities entities,
        PropertyDescriptor descriptor
    ) {
        try {
            return descriptor.getReadMethod().invoke(entities);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException(
                String.format("Failed to read property value: %s", descriptor.getName()),
                exception
            );
        }
    }

    private void scoreField(
        Map<String, Double> scores,
        List<String> queue,
        String key,
        Object value
    ) {
        double score = scoreValue(value);
        scores.put(key, score);

        if (score < scoringConfig.lowConfidenceThreshold()) {
            queue.add(key);
        }
    }

    private double scoreValue(Object value) {
        if (Objects.isNull(value)) {
            return 0.0;
        }

        String str = value.toString().strip();

        if (str.isEmpty()) {
            return 0.0;
        }

        return switch (classifyLength(str.length())) {
            case SHORT -> 0.5;
            case LONG -> 1.0;
        };
    }

    private double computeCompleteness(Map<String, Double> scores) {
        long nonNull = scoringConfig.criticalFields().stream()
                                    .filter(f -> scores.getOrDefault(f, 0.0) > 0.0)
                                    .count();

        return (double) nonNull / scoringConfig.criticalFields().size();
    }

    private LengthCategory classifyLength(int length) {
        if (length < scoringConfig.shortTextLengthThreshold()) {
            return LengthCategory.SHORT;
        }

        return LengthCategory.LONG;
    }

    private enum LengthCategory {
        SHORT,
        LONG
    }
}
