package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.domain.model.AnalysisStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class JobStatusResponse {

    private final Long jobId;
    private final AnalysisStatus status;
    private final int progress;
    private final Instant submittedAt;
    private final Instant completedAt;
    private final String errorMessage;
    private final String originalFilename;
    private final int pageCount;
}
