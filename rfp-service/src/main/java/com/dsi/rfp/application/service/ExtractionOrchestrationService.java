package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.persistence.ExtractionStateCheckpointRepository;
import com.dsi.rfp.agent.ExtractionGraph;
import com.dsi.rfp.agent.ExtractionState;
import com.dsi.rfp.domain.exception.ExtractionOrchestrationException;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.port.out.JobStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionOrchestrationService {

    private final ExtractionGraph extractionGraph;
    private final JobStatePort jobStatePort;
    private final ExtractionStateCheckpointRepository checkpointRepository;

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

        invokeGraph(
            initialState(
                jobId,
                documentPath
            ),
            jobId
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
        Path documentPath
    ) {
        return checkpointRepository.load(jobId)
                                   .map(this::copyStateData)
                                   .orElseGet(() -> ExtractionState.initial(
                                       jobId,
                                       documentPath.toString()
                                   ));
    }

    private Map<String, Object> copyStateData(ExtractionState state) {
        return new HashMap<>(state.data());
    }

    private void invokeGraph(
        Map<String, Object> initialState,
        Long jobId
    ) {
        try {
            extractionGraph.compile().invoke(initialState);
        } catch (GraphStateException exception) {
            throw new ExtractionOrchestrationException(
                String.format("Failed to run extraction graph for jobId=%s", jobId),
                exception
            );
        }
    }
}
