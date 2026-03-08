package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.extraction.DocumentValidationService;
import com.dsi.rfp.adapter.persistence.entity.DocumentEntity;
import com.dsi.rfp.adapter.persistence.repository.DocumentRepository;
import com.dsi.rfp.domain.exception.DocumentUnsupportedTypeException;
import com.dsi.rfp.domain.exception.DocumentValidationException;
import com.dsi.rfp.domain.exception.FileSizeLimitExceededException;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.model.ValidationResult;
import com.dsi.rfp.domain.port.out.FileStoragePort;
import com.dsi.rfp.domain.port.out.JobStatePort;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Service
public class RfpSubmissionService {

    private final DocumentValidationService validationService;
    private final FileStoragePort fileStoragePort;
    private final JobStatePort jobStatePort;
    private final DocumentRepository documentRepository;
    private final ExtractionOrchestrationService orchestrationService;

    public RfpSubmissionService(
        DocumentValidationService validationService,
        FileStoragePort fileStoragePort,
        JobStatePort jobStatePort,
        DocumentRepository documentRepository,
        ExtractionOrchestrationService orchestrationService
    ) {
        this.validationService = validationService;
        this.fileStoragePort = fileStoragePort;
        this.jobStatePort = jobStatePort;
        this.documentRepository = documentRepository;
        this.orchestrationService = orchestrationService;
    }

    @Transactional
    public Long submit(
        String originalFilename,
        byte[] fileContent
    ) {
        Path tempFile = writeTempFile(fileContent);

        ValidationResult result = validationService.validate(
            tempFile, fileContent.length
        );

        handleValidationResult(result);

        String checksum = computeSha256(fileContent);

        DocumentEntity document = createDocument(
            originalFilename,
            fileContent.length,
            checksum
        );

        ExtractionJob job = ExtractionJob.builder()
                                         .status(AnalysisStatus.QUEUED)
                                         .documentId(document.getId())
                                         .originalFilename(originalFilename)
                                         .build();

        ExtractionJob saved = jobStatePort.save(job);
        Long jobId = saved.getJobId();

        Path storedFile = fileStoragePort.store(
            jobId,
            fileContent,
            originalFilename
        );

        orchestrationService.runExtraction(
            jobId,
            storedFile
        );

        log.info(
            "event=rfp.submitted component=RfpSubmissionService jobId={} filename={}",
            jobId,
            originalFilename
        );

        return jobId;
    }

    private void handleValidationResult(ValidationResult result) {
        if (result.isValid()) {
            return;
        }
        switch (result.getErrorCode()) {
            case FILE_TOO_LARGE -> throw new FileSizeLimitExceededException(
                0, 0
            );
            case UNSUPPORTED_TYPE -> throw new DocumentUnsupportedTypeException(
                result.getErrorMessage()
            );
            default -> throw new DocumentValidationException(
                result.getErrorCode(),
                result.getErrorMessage()
            );
        }
    }

    private DocumentEntity createDocument(
        String filename,
        long size,
        String checksum
    ) {
        return documentRepository.findBySha256Checksum(checksum)
                                 .orElseGet(() -> {
                                     DocumentEntity doc = DocumentEntity.builder()
                                                                        .originalFilename(filename)
                                                                        .contentType(MediaType.APPLICATION_PDF_VALUE)
                                                                        .fileSizeBytes(size)
                                                                        .storagePath(StringUtils.EMPTY)
                                                                        .sha256Checksum(checksum)
                                                                        .build();
                                     return documentRepository.save(doc);
                                 });
    }

    private Path writeTempFile(byte[] content) {
        try {
            return Files.write(
                Files.createTempFile("rfp-upload-", ".tmp"),
                content
            );
        } catch (IOException e) {
            throw new UncheckedIOException(
                "Failed to write temp file for validation",
                e
            );
        }
    }

    private String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
