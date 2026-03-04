package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.extraction.PdfDocumentLoader;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.port.out.JobStatePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Service
public class ExtractionPipelineService {

    private final PageClassificationService classificationService;
    private final JobStatePort jobStatePort;
    private final PdfDocumentLoader pdfLoader;

    public ExtractionPipelineService(
        PageClassificationService classificationService,
        JobStatePort jobStatePort,
        PdfDocumentLoader pdfLoader
    ) {
        this.classificationService = classificationService;
        this.jobStatePort = jobStatePort;
        this.pdfLoader = pdfLoader;
    }

    @Async("rfpTaskExecutor")
    public void runAsync(Long jobId, Path pdfPath) {
        log.info(
            "event=pipeline.started component=ExtractionPipelineService jobId={}",
            jobId
        );

        jobStatePort.updateStatus(
            jobId,
            AnalysisStatus.RUNNING
        );

        classifyPages(
            jobId,
            pdfPath
        );

        updatePageCount(
            jobId,
            pdfPath
        );

        jobStatePort.updateStatus(
            jobId,
            AnalysisStatus.COMPLETED
        );

        log.info(
            "event=pipeline.completed component=ExtractionPipelineService jobId={}",
            jobId
        );
    }

    private void classifyPages(
        Long jobId,
        Path pdfPath
    ) {
        try {
            classificationService.classifyPages(
                jobId,
                pdfPath
            );
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format(
                    "Page classification failed for job %s",
                    jobId
                ),
                exception
            );
        }
    }

    private void updatePageCount(Long jobId, Path pdfPath) {
        try {
            int pageCount = pdfLoader.getPageCount(pdfPath);

            jobStatePort.updateProgress(jobId, 100);

            var job = jobStatePort.findById(jobId);

            job.ifPresent(j -> {
                var updated = ExtractionJob.builder()
                                           .jobId(j.getJobId())
                                           .status(j.getStatus())
                                           .submittedAt(j.getSubmittedAt())
                                           .completedAt(j.getCompletedAt())
                                           .documentId(j.getDocumentId())
                                           .progress(100)
                                           .errorMessage(j.getErrorMessage())
                                           .pageCount(pageCount)
                                           .originalFilename(j.getOriginalFilename())
                                           .build();
                jobStatePort.save(updated);
            });
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format("Failed to get page count for job %s", jobId),
                exception
            );
        }
    }
}
