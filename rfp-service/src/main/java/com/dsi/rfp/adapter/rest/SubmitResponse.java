package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.domain.model.AnalysisStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubmitResponse {

    private final Long jobId;
    private final AnalysisStatus status;
    private final String message;
}
