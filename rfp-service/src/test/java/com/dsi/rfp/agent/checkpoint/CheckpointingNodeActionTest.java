package com.dsi.rfp.agent.checkpoint;

import com.dsi.rfp.adapter.persistence.AgentExecutionTracker;
import com.dsi.rfp.adapter.persistence.ExtractionStateCheckpointRepository;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.model.AgentStepType;
import com.dsi.rfp.domain.model.StepOutcome;
import org.bsc.langgraph4j.action.NodeAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckpointingNodeActionTest {

    @Mock
    private NodeAction<ExtractionState> delegate;
    @Mock
    private ExtractionStateCheckpointRepository checkpointRepository;
    @Mock
    private AgentExecutionTracker executionTracker;

    private CheckpointingNodeAction action;

    @BeforeEach
    void setUp() {
        action = new CheckpointingNodeAction(
            delegate,
            checkpointRepository,
            executionTracker,
            AgentStepType.EXTRACT_TEXT
        );
    }

    @Test
    void shouldRecordSuccessfulStep() throws Exception {
        Map<String, Object> stateData = new HashMap<>();
        stateData.put(ExtractionState.Key.JOB_ID.value(), 42L);
        stateData.put(ExtractionState.Key.EXECUTION_ID.value(), 100L);
        stateData.put(ExtractionState.Key.STEP_SEQUENCE.value(), 2);

        ExtractionState state = new ExtractionState(stateData);
        when(delegate.apply(state)).thenReturn(Map.of("key", "value"));

        Map<String, Object> result = action.apply(state);

        verify(executionTracker).recordStep(
            eq(100L),
            eq(AgentStepType.EXTRACT_TEXT),
            eq(3),
            any(),
            any(),
            eq(StepOutcome.SUCCESS),
            isNull()
        );

        assertThat(result)
            .containsEntry("key", "value")
            .containsEntry(
                ExtractionState.Key.STEP_SEQUENCE.value(),
                3
            );
    }

    @Test
    void shouldCheckpointAfterSuccess() throws Exception {
        Map<String, Object> stateData = new HashMap<>();
        stateData.put(ExtractionState.Key.JOB_ID.value(), 42L);
        stateData.put(ExtractionState.Key.EXECUTION_ID.value(), 100L);
        stateData.put(ExtractionState.Key.STEP_SEQUENCE.value(), 0);

        ExtractionState state = new ExtractionState(stateData);
        when(delegate.apply(state)).thenReturn(Map.of("key", "value"));

        action.apply(state);

        verify(checkpointRepository).save(eq(42L), any());
    }

    @Test
    void shouldIncrementSequenceFromCurrentState() throws Exception {
        Map<String, Object> stateData = new HashMap<>();
        stateData.put(ExtractionState.Key.JOB_ID.value(), 42L);
        stateData.put(ExtractionState.Key.EXECUTION_ID.value(), 100L);
        stateData.put(ExtractionState.Key.STEP_SEQUENCE.value(), 5);

        ExtractionState state = new ExtractionState(stateData);
        when(delegate.apply(state)).thenReturn(Map.of());

        Map<String, Object> result = action.apply(state);

        assertThat(result.get(ExtractionState.Key.STEP_SEQUENCE.value()))
            .isEqualTo(6);

        verify(executionTracker).recordStep(
            eq(100L),
            eq(AgentStepType.EXTRACT_TEXT),
            eq(6),
            any(),
            any(),
            eq(StepOutcome.SUCCESS),
            isNull()
        );
    }
}
