package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.extraction.DocumentValidationService;
import com.dsi.rfp.adapter.persistence.entity.DocumentEntity;
import com.dsi.rfp.adapter.persistence.repository.DocumentRepository;
import com.dsi.rfp.adapter.persistence.repository.UserRepository;
import com.dsi.rfp.domain.exception.DocumentEncryptedException;
import com.dsi.rfp.domain.exception.DocumentUnsupportedTypeException;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.model.ValidationErrorCode;
import com.dsi.rfp.domain.model.ValidationResult;
import com.dsi.rfp.domain.port.out.FileStoragePort;
import com.dsi.rfp.domain.port.out.JobStatePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RfpSubmissionServiceTest {

    @Mock
    private DocumentValidationService validationService;
    @Mock
    private FileStoragePort fileStoragePort;
    @Mock
    private JobStatePort jobStatePort;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ExtractionOrchestrationService orchestrationService;

    private RfpSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new RfpSubmissionService(
            validationService, fileStoragePort, jobStatePort,
            documentRepository, userRepository, orchestrationService
        );
    }

    @Test
    void shouldReturnJobIdOnValidSubmit() {
        Path stored = Path.of("/tmp/test/doc.pdf.enc");
        Path decrypted = Path.of("/tmp/test/doc.pdf");
        when(validationService.validate(any(Path.class), anyLong()))
            .thenReturn(ValidationResult.ok());
        when(documentRepository.findBySha256Checksum(anyString()))
            .thenReturn(Optional.empty());
        DocumentEntity doc = DocumentEntity.builder()
                                           .originalFilename("test.pdf")
                                           .contentType("application/pdf")
                                           .fileSizeBytes(100L)
                                           .storagePath(stored.toString())
                                           .sha256Checksum("abc")
                                           .build();
        doc.setId(1L);
        when(documentRepository.save(any())).thenReturn(doc);
        ExtractionJob savedJob = ExtractionJob.builder()
                                              .jobId(42L)
                                              .status(AnalysisStatus.QUEUED)
                                              .documentId(1L)
                                              .originalFilename("test.pdf")
                                              .build();
        when(jobStatePort.save(any())).thenReturn(savedJob);
        when(fileStoragePort.store(eq(42L), any(), anyString()))
            .thenReturn(stored);
        when(fileStoragePort.retrieve(eq(42L), eq("test.pdf")))
            .thenReturn(decrypted);

        Long result = service.submit("test.pdf", "content".getBytes());

        assertThat(result).isEqualTo(42L);
        verify(orchestrationService).runExtraction(eq(42L), eq(decrypted));
    }

    @Test
    void shouldThrowForEncryptedPdf() {
        when(validationService.validate(any(Path.class), anyLong()))
            .thenThrow(new DocumentEncryptedException("encrypted"));

        assertThatThrownBy(
            () -> service.submit("enc.pdf", "data".getBytes())
        ).isInstanceOf(DocumentEncryptedException.class);
    }

    @Test
    void shouldThrowForUnsupportedType() {
        when(validationService.validate(any(Path.class), anyLong()))
            .thenReturn(
                ValidationResult.fail(ValidationErrorCode.UNSUPPORTED_TYPE, "text/plain")
            );

        assertThatThrownBy(
            () -> service.submit("bad.txt", "data".getBytes())
        ).isInstanceOf(DocumentUnsupportedTypeException.class);
    }

    @Test
    void shouldSaveJobBeforeDispatching() {
        Path stored = Path.of("/tmp/test/doc.pdf.enc");
        Path decrypted = Path.of("/tmp/test/doc.pdf");
        when(validationService.validate(any(Path.class), anyLong()))
            .thenReturn(ValidationResult.ok());
        when(documentRepository.findBySha256Checksum(anyString()))
            .thenReturn(Optional.empty());
        DocumentEntity doc = DocumentEntity.builder()
                                           .originalFilename("test.pdf")
                                           .contentType("application/pdf")
                                           .fileSizeBytes(100L)
                                           .storagePath(stored.toString())
                                           .sha256Checksum("abc")
                                           .build();
        doc.setId(1L);
        when(documentRepository.save(any())).thenReturn(doc);
        ExtractionJob savedJob = ExtractionJob.builder()
                                              .jobId(42L)
                                              .status(AnalysisStatus.QUEUED)
                                              .documentId(1L)
                                              .originalFilename("test.pdf")
                                              .build();
        when(jobStatePort.save(any())).thenReturn(savedJob);
        when(fileStoragePort.store(eq(42L), any(), anyString()))
            .thenReturn(stored);
        when(fileStoragePort.retrieve(eq(42L), eq("test.pdf")))
            .thenReturn(decrypted);

        service.submit("test.pdf", "data".getBytes());

        var inOrder = inOrder(jobStatePort, orchestrationService);
        inOrder.verify(jobStatePort).save(any(ExtractionJob.class));
        inOrder.verify(orchestrationService).runExtraction(eq(42L), eq(decrypted));
    }

    @Test
    void shouldReuseDuplicateDocument() {
        Path stored = Path.of("/tmp/test/doc.pdf.enc");
        Path decrypted = Path.of("/tmp/test/doc.pdf");
        when(validationService.validate(any(Path.class), anyLong()))
            .thenReturn(ValidationResult.ok());
        DocumentEntity existing = DocumentEntity.builder()
                                                .originalFilename("existing.pdf")
                                                .contentType("application/pdf")
                                                .fileSizeBytes(100L)
                                                .storagePath(stored.toString())
                                                .sha256Checksum("abc")
                                                .build();
        existing.setId(1L);
        when(documentRepository.findBySha256Checksum(anyString()))
            .thenReturn(Optional.of(existing));
        ExtractionJob savedJob = ExtractionJob.builder()
                                              .jobId(42L)
                                              .status(AnalysisStatus.QUEUED)
                                              .documentId(1L)
                                              .originalFilename("test.pdf")
                                              .build();
        when(jobStatePort.save(any())).thenReturn(savedJob);
        when(fileStoragePort.store(eq(42L), any(), anyString()))
            .thenReturn(stored);
        when(fileStoragePort.retrieve(eq(42L), eq("test.pdf")))
            .thenReturn(decrypted);

        service.submit("test.pdf", "data".getBytes());

        verify(documentRepository, never()).save(any());
    }
}
