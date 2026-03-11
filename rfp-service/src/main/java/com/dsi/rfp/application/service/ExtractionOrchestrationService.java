package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.persistence.AgentExecutionTracker;
import com.dsi.rfp.adapter.persistence.ExtractionStateCheckpointRepository;
import com.dsi.rfp.agent.ExtractionGraph;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.exception.ExtractionOrchestrationException;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.model.TerminationReason;
import com.dsi.rfp.domain.port.out.JobStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionOrchestrationService {

    private final ExtractionGraph extractionGraph;
    private final JobStatePort jobStatePort;
    private final ExtractionStateCheckpointRepository checkpointRepository;
    private final AgentExecutionTracker executionTracker;

    @Async("rfpTaskExecutor")
    public void runExtraction(Long jobId, Path documentPath) {
        log.info(
            "event=extraction.start component=ExtractionOrchestrationService jobId={} path={}",
            jobId,
            documentPath
        );

        jobStatePort.updateStatus(
            jobId,
            AnalysisStatus.RUNNING
        );

        Long executionId = executionTracker.startExecution(jobId);

        Map<String, Object> state = initialState(
            jobId,
            documentPath,
            executionId
        );

        Optional<ExtractionState> finalState = invokeGraph(
            state,
            jobId,
            executionId
        );

        int completedSteps = finalState
            .map(ExtractionState::stepSequence)
            .orElse(0);

        int repairIterations = finalState
            .map(ExtractionState::totalRepairIterations)
            .orElse(0);

        executionTracker.completeExecution(
            executionId,
            TerminationReason.SUCCESS,
            completedSteps,
            completedSteps,
            repairIterations
        );

        jobStatePort.updateStatus(
            jobId,
            AnalysisStatus.COMPLETED
        );

        log.info(
            "event=extraction.complete component=ExtractionOrchestrationService jobId={}",
            jobId
        );
    }

    private Map<String, Object> initialState(
        Long jobId,
        Path documentPath,
        Long executionId
    ) {
        Map<String, Object> state = checkpointRepository.load(jobId)
                                                        .map(this::copyStateData)
                                                        .orElseGet(() -> ExtractionState.initial(
                                                            jobId,
                                                            documentPath.toString()
                                                        ));

        state.put(
            ExtractionState.Key.EXECUTION_ID.value(),
            executionId
        );

        return state;
    }

    private Map<String, Object> copyStateData(ExtractionState state) {
        return new HashMap<>(state.data());
    }

    private Optional<ExtractionState> invokeGraph(
        Map<String, Object> initialState,
        Long jobId,
        Long executionId
    ) {
        try {
            return extractionGraph.compile().invoke(initialState);
        } catch (GraphStateException exception) {
            executionTracker.failExecution(
                executionId,
                TerminationReason.ERROR
            );

            throw new ExtractionOrchestrationException(
                String.format(
                    "Failed to run extraction graph for jobId=%s",
                    jobId
                ),
                exception
            );
        }
    }
}
