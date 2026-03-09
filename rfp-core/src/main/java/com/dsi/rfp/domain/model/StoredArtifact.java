package com.dsi.rfp.domain.model;

public record StoredArtifact(
    String filename,
    byte[] content
) {
}
