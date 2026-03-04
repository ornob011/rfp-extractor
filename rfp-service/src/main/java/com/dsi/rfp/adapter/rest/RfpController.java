package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.application.service.RfpJobService;
import com.dsi.rfp.application.service.RfpSubmissionService;
import com.dsi.rfp.domain.model.AnalysisStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/rfp")
public class RfpController {

    private final RfpSubmissionService submissionService;
    private final RfpJobService jobService;

    public RfpController(
        RfpSubmissionService submissionService,
        RfpJobService jobService
    ) {
        this.submissionService = submissionService;
        this.jobService = jobService;
    }

    @PostMapping("/submit")
    public ResponseEntity<SubmitResponse> submit(
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        Long jobId = submissionService.submit(
            file.getOriginalFilename(),
            file.getBytes()
        );

        SubmitResponse response = SubmitResponse.builder()
                                                .jobId(jobId)
                                                .status(AnalysisStatus.QUEUED)
                                                .message("RFP document submitted for processing")
                                                .build();

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                             .body(response);
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<JobStatusResponse> getStatus(
        @PathVariable Long jobId
    ) {
        return jobService.findById(jobId)
                         .map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobStatusResponse>> listJobs() {
        return ResponseEntity.ok(jobService.findAll());
    }
}
