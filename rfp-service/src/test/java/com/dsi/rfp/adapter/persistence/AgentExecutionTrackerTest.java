package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.persistence.entity.AgentExecutionEntity;
import com.dsi.rfp.adapter.persistence.entity.AgentStepEntity;
import com.dsi.rfp.adapter.persistence.entity.AnalysisJobEntity;
import com.dsi.rfp.adapter.persistence.repository.AgentExecutionRepository;
import com.dsi.rfp.adapter.persistence.repository.AgentStepRepository;
import com.dsi.rfp.adapter.persistence.repository.AnalysisJobRepository;
import com.dsi.rfp.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentExecutionTrackerTest {

    @Mock
    private AgentExecutionRepository executionRepository;
    @Mock
    private AgentStepRepository stepRepository;
    @Mock
    private AnalysisJobRepository jobRepository;

    private AgentExecutionTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new AgentExecutionTracker(
            executionRepository,
            stepRepository,
            jobRepository
        );
    }

    @Test
    void startExecutionShouldCreateRunningEntity() {
        AnalysisJobEntity job = new AnalysisJobEntity();
        when(jobRepository.getReferenceById(42L)).thenReturn(job);

        AgentExecutionEntity saved = AgentExecutionEntity.builder()
            .status(ExecutionStatus.RUNNING)
            .build();
        saved.setId(100L);

        when(executionRepository.save(any())).thenReturn(saved);

        Long executionId = tracker.startExecution(42L);

        assertThat(executionId).isEqualTo(100L);

        ArgumentCaptor<AgentExecutionEntity> captor =
            ArgumentCaptor.forClass(AgentExecutionEntity.class);
        verify(executionRepository).save(captor.capture());

        AgentExecutionEntity captured = captor.getValue();
        assertThat(captured.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(captured.getAnalysisJob()).isSameAs(job);
        assertThat(captured.getStartedAt()).isNotNull();
    }

    @Test
    void recordStepShouldSaveStepEntity() {
        AgentExecutionEntity execution = new AgentExecutionEntity();
        when(executionRepository.getReferenceById(100L))
            .thenReturn(execution);

        Instant start = Instant.now();
        Instant end = start.plusMillis(500);

        tracker.recordStep(
            100L,
            AgentStepType.EXTRACT_TEXT,
            3,
            start,
            end,
            StepOutcome.SUCCESS,
            null
        );

        ArgumentCaptor<AgentStepEntity> captor =
            ArgumentCaptor.forClass(AgentStepEntity.class);
        verify(stepRepository).save(captor.capture());

        AgentStepEntity step = captor.getValue();
        assertThat(step.getExecution()).isSameAs(execution);
        assertThat(step.getStepType()).isEqualTo(AgentStepType.EXTRACT_TEXT);
        assertThat(step.getSequence()).isEqualTo(3);
        assertThat(step.getOutcome()).isEqualTo(StepOutcome.SUCCESS);
        assertThat(step.getDurationMs()).isEqualTo(500L);
        assertThat(step.getErrorMessage()).isNull();
    }

    @Test
    void completeExecutionShouldUpdateToCompleted() {
        AgentExecutionEntity execution = AgentExecutionEntity.builder()
            .status(ExecutionStatus.RUNNING)
            .build();
        when(executionRepository.findById(100L))
            .thenReturn(Optional.of(execution));

        tracker.completeExecution(
            100L,
            TerminationReason.SUCCESS,
            10,
            10,
            2
        );

        verify(executionRepository).save(execution);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(execution.getTerminationReason())
            .isEqualTo(TerminationReason.SUCCESS);
        assertThat(execution.getTotalSteps()).isEqualTo(10);
        assertThat(execution.getCompletedSteps()).isEqualTo(10);
        assertThat(execution.getRepairIterations()).isEqualTo(2);
        assertThat(execution.getCompletedAt()).isNotNull();
    }

    @Test
    void failExecutionShouldUpdateToFailed() {
        AgentExecutionEntity execution = AgentExecutionEntity.builder()
            .status(ExecutionStatus.RUNNING)
            .build();
        when(executionRepository.findById(100L))
            .thenReturn(Optional.of(execution));

        tracker.failExecution(100L, TerminationReason.ERROR);

        verify(executionRepository).save(execution);
        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getTerminationReason())
            .isEqualTo(TerminationReason.ERROR);
        assertThat(execution.getCompletedAt()).isNotNull();
    }
}
