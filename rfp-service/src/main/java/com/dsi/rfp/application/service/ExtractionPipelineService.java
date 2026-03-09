package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.extraction.PdfDocumentLoader;
import com.dsi.rfp.adapter.extraction.SectionSegmenter;
import com.dsi.rfp.domain.exception.SystemIoException;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.model.Section;
import com.dsi.rfp.domain.port.out.JobStatePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
public class ExtractionPipelineService {

    private final PageClassificationService classificationService;
    private final JobStatePort jobStatePort;
    private final PdfDocumentLoader pdfLoader;
    private final SectionSegmenter sectionSegmenter;
    private final ObjectMapper objectMapper;

    public ExtractionPipelineService(
        PageClassificationService classificationService,
        JobStatePort jobStatePort,
        PdfDocumentLoader pdfLoader,
        SectionSegmenter sectionSegmenter,
        ObjectMapper objectMapper
    ) {
        this.classificationService = classificationService;
        this.jobStatePort = jobStatePort;
        this.pdfLoader = pdfLoader;
        this.sectionSegmenter = sectionSegmenter;
        this.objectMapper = objectMapper;
    }

    @Async("rfpTaskExecutor")
    public void runAsync(Long jobId, Path pdfPath) {
        log.info(
            "event=pipeline.started component=ExtractionPipelineService jobId={}",
            jobId
        );

        jobStatePort.updateStatus(jobId, AnalysisStatus.RUNNING);

        classifyPages(
            jobId,
            pdfPath
        );

        segmentSections(
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

    private void classifyPages(Long jobId, Path pdfPath) {
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

    private void segmentSections(Long jobId, Path pdfPath) {
        try {
            int pageCount = pdfLoader.getPageCount(pdfPath);

            List<Section> sections = sectionSegmenter.segment(
                pdfPath,
                pdfLoader,
                pageCount
            );

            JsonNode sectionsJson = objectMapper.valueToTree(sections);

            jobStatePort.updateSectionsJson(
                jobId,
                sectionsJson
            );

            log.info(
                "event=sections.segmented component=ExtractionPipelineService jobId={} sectionCount={}",
                jobId,
                sections.size()
            );
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format(
                    "Section segmentation failed for job %s",
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

            job.ifPresent(extractionJob -> {
                var updated = ExtractionJob.builder()
                                           .jobId(extractionJob.getJobId())
                                           .status(extractionJob.getStatus())
                                           .submittedAt(extractionJob.getSubmittedAt())
                                           .completedAt(extractionJob.getCompletedAt())
                                           .documentId(extractionJob.getDocumentId())
                                           .progress(100)
                                           .errorMessage(extractionJob.getErrorMessage())
                                           .pageCount(pageCount)
                                           .originalFilename(extractionJob.getOriginalFilename())
                                           .sectionsJson(extractionJob.getSectionsJson())
                                           .build();
                jobStatePort.save(updated);
            });
        } catch (IOException exception) {
            throw new SystemIoException(
                String.format(
                    "Failed to get page count for job %s",
                    jobId
                ),
                exception
            );
        }
    }
}
