package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.domain.model.AnalysisStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

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

    @Builder.Default
    private final List<RepairEventDto> repairEvents = List.of();

    @Builder.Default
    private final int totalRepairIterations = 0;

    @Builder.Default
    private final int lowConfidenceQueueSize = 0;
}
