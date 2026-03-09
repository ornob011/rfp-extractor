package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.domain.model.ArtifactFileType;
import lombok.Builder;

import java.time.Instant;

@Builder
public record ArtifactMetadataDto(
    Long jobId,
    String filename,
    ArtifactFileType fileType,
    long sizeBytes,
    Instant generatedAt,
    String downloadUrl
) {
}
