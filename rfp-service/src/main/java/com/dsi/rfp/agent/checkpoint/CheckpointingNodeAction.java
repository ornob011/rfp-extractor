package com.dsi.rfp.agent.checkpoint;

import com.dsi.rfp.adapter.persistence.AgentExecutionTracker;
import com.dsi.rfp.adapter.persistence.ExtractionStateCheckpointRepository;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.AgentStepType;
import com.dsi.rfp.domain.model.StepOutcome;
import org.bsc.langgraph4j.action.NodeAction;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class CheckpointingNodeAction implements NodeAction<ExtractionState> {

    private final NodeAction<ExtractionState> delegate;
    private final ExtractionStateCheckpointRepository checkpointRepository;
    private final AgentExecutionTracker executionTracker;
    private final AgentStepType stepType;

    public CheckpointingNodeAction(
        NodeAction<ExtractionState> delegate,
        ExtractionStateCheckpointRepository checkpointRepository,
        AgentExecutionTracker executionTracker,
        AgentStepType stepType
    ) {
        this.delegate = delegate;
        this.checkpointRepository = checkpointRepository;
        this.executionTracker = executionTracker;
        this.stepType = stepType;
    }

    @Override
    public Map<String, Object> apply(
        ExtractionState state
    ) throws Exception {
        Long executionId = state.executionId();
        int sequence = state.stepSequence() + 1;
        Instant startedAt = Instant.now();

        Map<String, Object> updates = delegate.apply(state);

        Instant completedAt = Instant.now();
        executionTracker.recordStep(
            executionId,
            stepType,
            sequence,
            startedAt,
            completedAt,
            StepOutcome.SUCCESS,
            null
        );

        Map<String, Object> enriched = new HashMap<>(updates);
        enriched.put(
            ExtractionState.Key.STEP_SEQUENCE.value(),
            sequence
        );

        checkpointRepository.save(
            state.jobId(),
            mergeState(state, enriched)
        );

        return enriched;
    }

    private ExtractionState mergeState(
        ExtractionState state,
        Map<String, Object> updates
    ) {
        Map<String, Object> merged = new HashMap<>(state.data());
        merged.putAll(updates);

        return new ExtractionState(merged);
    }
}
