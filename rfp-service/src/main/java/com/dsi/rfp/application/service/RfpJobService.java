package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.rest.JobStatusResponse;
import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.port.out.JobStatePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class RfpJobService {

    private final JobStatePort jobStatePort;

    public RfpJobService(JobStatePort jobStatePort) {
        this.jobStatePort = jobStatePort;
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
