package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.rest.JobStatusResponse;
import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.port.out.JobStatePort;
import com.dsi.rfp.domain.port.out.ResultPersistencePort;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class RfpJobService {

    private final JobStatePort jobStatePort;
    private final ResultPersistencePort resultPersistencePort;

    public RfpJobService(
        JobStatePort jobStatePort,
        ResultPersistencePort resultPersistencePort
    ) {
        this.jobStatePort = jobStatePort;
        this.resultPersistencePort = resultPersistencePort;
    }

    public Optional<JobStatusResponse> findById(Long jobId) {
        return jobStatePort.findById(jobId)
                           .map(this::toResponse);
    }

    public List<JobStatusResponse> findAll() {
        return jobStatePort.findAll().stream()
                           .map(this::toResponse)
                           .toList();
    }

    public JsonNode getSectionsJson(Long jobId) {
        return jobStatePort.findById(jobId)
                           .map(ExtractionJob::getSectionsJson)
                           .orElseThrow(() -> new EntityNotFoundException(
                               String.format("Job not found: %s", jobId)
                           ));
    }

    public Optional<JsonNode> getResult(Long jobId) {
        return resultPersistencePort.findResult(jobId);
    }

    private JobStatusResponse toResponse(ExtractionJob job) {
        return JobStatusResponse.builder()
                                .jobId(job.getJobId())
                                .status(job.getStatus())
                                .progress(job.getProgress())
                                .submittedAt(job.getSubmittedAt())
                                .completedAt(job.getCompletedAt())
                                .errorMessage(job.getErrorMessage())
                                .originalFilename(job.getOriginalFilename())
                                .pageCount(job.getPageCount())
                                .build();
    }
}
