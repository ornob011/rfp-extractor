package com.dsi.rfp.domain.port.out;

import com.dsi.rfp.domain.model.ArtifactMetadata;
import com.dsi.rfp.domain.model.StoredArtifact;

import java.util.List;

public interface ArtifactPort {

    ArtifactMetadata storeArtifact(
        Long jobId,
        String filename,
        byte[] content
    );

    List<ArtifactMetadata> listArtifacts(Long jobId);

    byte[] loadArtifact(
        Long jobId,
        String filename
    );

    void deleteArtifacts(Long jobId);

    List<ArtifactMetadata> replaceArtifacts(
        Long jobId,
        List<StoredArtifact> artifacts
    );
}
