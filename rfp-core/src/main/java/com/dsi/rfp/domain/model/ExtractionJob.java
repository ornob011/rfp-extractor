package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ExtractionJob {

    private final Long jobId;
    private final AnalysisStatus status;
    private final Instant submittedAt;
    private final Instant completedAt;
    private final Long documentId;
    private final int progress;
    private final String errorMessage;
    private final String userId;
    private final int pageCount;
    private final String originalFilename;
}
