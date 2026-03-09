package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.security.FileEncryptionService;
import com.dsi.rfp.domain.exception.DataIntegrityException;
import com.dsi.rfp.domain.port.out.FileStoragePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Primary
@Component
public class EncryptedDocumentStorageAdapter implements FileStoragePort {

    private static final String ENC_EXTENSION = ".enc";

    private final LocalFileStorageAdapter delegate;
    private final FileEncryptionService encryptionService;

    public EncryptedDocumentStorageAdapter(
        LocalFileStorageAdapter delegate,
        FileEncryptionService encryptionService
    ) {
        this.delegate = delegate;
        this.encryptionService = encryptionService;
    }

    @Override
    public Path store(
        Long jobId,
        byte[] content,
        String filename
    ) {
        byte[] onDisk = encryptionService.encrypt(content);

        String encFilename = String.format("%s%s", filename, ENC_EXTENSION);
        Path stored = delegate.store(jobId, onDisk, encFilename);

        log.info(
            "event=file.encrypted component=EncryptedDocumentStorageAdapter jobId={} file={}",
            jobId,
            stored.getFileName()
        );

        return stored;
    }

    @Override
    public Path retrieve(Long jobId, String filename) {
        String encFilename = String.format("%s%s", filename, ENC_EXTENSION);
        Path encryptedPath = delegate.retrieve(jobId, encFilename);
        return decryptToTemp(
            encryptedPath,
            filename
        );
    }

    @Override
    public Path jobDirectory(Long jobId) {
        return delegate.jobDirectory(jobId);
    }

    @Override
    public void deleteJobDirectory(Long jobId) {
        delegate.deleteJobDirectory(jobId);
    }

    private Path decryptToTemp(
        Path encryptedPath,
        String filename
    ) {
        try {
            byte[] encryptedData = Files.readAllBytes(encryptedPath);
            byte[] plaintext = encryptionService.decrypt(encryptedData);

            Path tempFile = Files.createTempFile("rfp-dec-", filename);
            Files.write(tempFile, plaintext);

            return tempFile;
        } catch (IOException exception) {
            throw new DataIntegrityException(
                String.format("Failed to decrypt file: %s", encryptedPath),
                exception
            );
        }
    }
}
