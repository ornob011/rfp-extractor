package com.dsi.rfp.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactMetadata {

    private Long jobId;
    private String filename;
    private ArtifactFileType fileType;
    private long sizeBytes;
    private Instant generatedAt;
    private String downloadUrl;
}
