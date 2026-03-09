package com.dsi.rfp.application.service;

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
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionOrchestrationService {

    private final ExtractionGraph extractionGraph;
    private final JobStatePort jobStatePort;

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

        Map<String, Object> initialState = ExtractionState.initial(
            jobId,
            documentPath.toString()
        );

        invokeGraph(
            initialState,
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
