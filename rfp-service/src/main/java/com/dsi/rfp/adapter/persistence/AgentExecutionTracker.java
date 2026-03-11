package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.persistence.entity.AgentExecutionEntity;
import com.dsi.rfp.adapter.persistence.entity.AgentStepEntity;
import com.dsi.rfp.adapter.persistence.entity.AnalysisJobEntity;
import com.dsi.rfp.adapter.persistence.repository.AgentExecutionRepository;
import com.dsi.rfp.adapter.persistence.repository.AgentStepRepository;
import com.dsi.rfp.adapter.persistence.repository.AnalysisJobRepository;
import com.dsi.rfp.domain.model.AgentStepType;
import com.dsi.rfp.domain.model.ExecutionStatus;
import com.dsi.rfp.domain.model.StepOutcome;
import com.dsi.rfp.domain.model.TerminationReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class AgentExecutionTracker {

    private final AgentExecutionRepository executionRepository;
    private final AgentStepRepository stepRepository;
    private final AnalysisJobRepository jobRepository;

    public AgentExecutionTracker(
        AgentExecutionRepository executionRepository,
        AgentStepRepository stepRepository,
        AnalysisJobRepository jobRepository
    ) {
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
        this.jobRepository = jobRepository;
    }

    public Long startExecution(Long jobId) {
        AnalysisJobEntity job = jobRepository.getReferenceById(jobId);

        AgentExecutionEntity execution = AgentExecutionEntity.builder()
                                                             .analysisJob(job)
                                                             .status(ExecutionStatus.RUNNING)
                                                             .startedAt(Instant.now())
                                                             .build();

        AgentExecutionEntity saved = executionRepository.save(execution);

        log.info(
            "event=execution.started component=AgentExecutionTracker"
            + " jobId={} executionId={}",
            jobId,
            saved.getId()
        );

        return saved.getId();
    }

    public void recordStep(
        Long executionId,
        AgentStepType stepType,
        int sequence,
        Instant startedAt,
        Instant completedAt,
        StepOutcome outcome,
        String errorMessage
    ) {
        AgentExecutionEntity execution =
            executionRepository.getReferenceById(executionId);

        AgentStepEntity step = AgentStepEntity.builder()
                                              .execution(execution)
                                              .stepType(stepType)
                                              .sequence(sequence)
                                              .startedAt(startedAt)
                                              .completedAt(completedAt)
                                              .durationMs(Duration.between(startedAt, completedAt).toMillis())
                                              .outcome(outcome)
                                              .errorMessage(errorMessage)
                                              .build();

        stepRepository.save(step);

        log.info(
            "event=step.recorded component=AgentExecutionTracker"
            + " executionId={} step={} sequence={} outcome={}"
            + " durationMs={}",
            executionId,
            stepType,
            sequence,
            outcome,
            step.getDurationMs()
        );
    }

    public void completeExecution(
        Long executionId,
        TerminationReason reason,
        int totalSteps,
        int completedSteps,
        int repairIterations
    ) {
        AgentExecutionEntity execution =
            executionRepository.findById(executionId).orElseThrow();

        execution.setStatus(ExecutionStatus.COMPLETED);
        execution.setTerminationReason(reason);
        execution.setCompletedAt(Instant.now());
        execution.setTotalSteps(totalSteps);
        execution.setCompletedSteps(completedSteps);
        execution.setRepairIterations(repairIterations);

        executionRepository.save(execution);

        log.info(
            "event=execution.completed component=AgentExecutionTracker"
            + " executionId={} reason={} totalSteps={}"
            + " completedSteps={} repairIterations={}",
            executionId,
            reason,
            totalSteps,
            completedSteps,
            repairIterations
        );
    }

    public void failExecution(
        Long executionId,
        TerminationReason reason
    ) {
        AgentExecutionEntity execution =
            executionRepository.findById(executionId).orElseThrow();

        execution.setStatus(ExecutionStatus.FAILED);
        execution.setTerminationReason(reason);
        execution.setCompletedAt(Instant.now());

        executionRepository.save(execution);

        log.info(
            "event=execution.failed component=AgentExecutionTracker"
            + " executionId={} reason={}",
            executionId,
            reason
        );
    }
}
