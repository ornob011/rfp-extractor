package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.security.FileEncryptionService;
import com.dsi.rfp.domain.model.ArtifactMetadata;
import com.dsi.rfp.domain.model.StoredArtifact;
import com.dsi.rfp.domain.port.out.ArtifactPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Primary
@Component
public class EncryptedArtifactStorageAdapter implements ArtifactPort {

    private final LocalArtifactStorageAdapter delegate;
    private final FileEncryptionService encryptionService;

    public EncryptedArtifactStorageAdapter(
        LocalArtifactStorageAdapter delegate,
        FileEncryptionService encryptionService
    ) {
        this.delegate = delegate;
        this.encryptionService = encryptionService;
    }

    @Override
    public ArtifactMetadata storeArtifact(
        Long jobId,
        String filename,
        byte[] content
    ) {
        byte[] onDisk = encryptionService.encrypt(content);

        log.info(
            "event=artifact.encrypted component=EncryptedArtifactStorageAdapter jobId={} filename={}",
            jobId,
            filename
        );

        return delegate.storeArtifact(jobId, filename, onDisk);
    }

    @Override
    public List<ArtifactMetadata> listArtifacts(Long jobId) {
        return delegate.listArtifacts(jobId);
    }

    @Override
    public byte[] loadArtifact(Long jobId, String filename) {
        byte[] encData = delegate.loadArtifact(jobId, filename);
        return encryptionService.decrypt(encData);
    }

    @Override
    public void deleteArtifacts(Long jobId) {
        delegate.deleteArtifacts(jobId);
    }

    @Override
    public List<ArtifactMetadata> replaceArtifacts(
        Long jobId,
        List<StoredArtifact> artifacts
    ) {
        List<StoredArtifact> encrypted = artifacts.stream()
            .map(artifact -> new StoredArtifact(
                artifact.filename(),
                encryptionService.encrypt(artifact.content())
            ))
            .toList();

        return delegate.replaceArtifacts(jobId, encrypted);
    }
}
