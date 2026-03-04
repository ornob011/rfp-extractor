package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.persistence.entity.AnalysisJobEntity;
import com.dsi.rfp.adapter.persistence.entity.DocumentEntity;
import com.dsi.rfp.adapter.persistence.repository.AnalysisJobRepository;
import com.dsi.rfp.adapter.persistence.repository.DocumentRepository;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.model.ExtractionJob;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaJobStateRepositoryTest {

    @Mock
    private AnalysisJobRepository analysisJobRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private AnalysisJobMapper mapper;

    private JpaJobStateRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JpaJobStateRepository(
            analysisJobRepository, documentRepository, mapper
        );
    }

    @Test
    void shouldReturnEmptyWhenJobNotFound() {
        Long jobId = 1L;
        when(analysisJobRepository.findById(jobId))
            .thenReturn(Optional.empty());
        Optional<ExtractionJob> result = repository.findById(jobId);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnJobWhenFound() {
        Long jobId = 1L;
        AnalysisJobEntity entity = buildEntity(jobId);
        ExtractionJob expected = buildJob(jobId);
        when(analysisJobRepository.findById(jobId))
            .thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(expected);

        Optional<ExtractionJob> result = repository.findById(jobId);

        assertThat(result).isPresent();
        assertThat(result.get().getJobId()).isEqualTo(jobId);
    }

    @Test
    void shouldUpdateStatusToRunning() {
        Long jobId = 1L;
        AnalysisJobEntity entity = buildEntity(jobId);
        when(analysisJobRepository.findById(jobId))
            .thenReturn(Optional.of(entity));
        when(analysisJobRepository.save(any())).thenReturn(entity);

        repository.updateStatus(jobId, AnalysisStatus.RUNNING);

        verify(analysisJobRepository).save(entity);
        assertThat(entity.getStatus())
            .isEqualTo(AnalysisStatus.RUNNING);
    }

    @Test
    void shouldReturnAllJobs() {
        Long jobId = 2L;
        AnalysisJobEntity entity = buildEntity(jobId);
        ExtractionJob job = buildJob(jobId);
        when(analysisJobRepository.findAll())
            .thenReturn(List.of(entity));
        when(mapper.toDomain(entity)).thenReturn(job);

        List<ExtractionJob> result = repository.findAll();

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldSaveNewJobWithDocumentLookup() {
        Long documentId = 10L;
        DocumentEntity doc = buildDocumentEntity(documentId);
        when(documentRepository.findById(documentId))
            .thenReturn(Optional.of(doc));

        ExtractionJob newJob = ExtractionJob.builder()
                                            .status(AnalysisStatus.QUEUED)
                                            .documentId(documentId)
                                            .originalFilename("test.pdf")
                                            .build();

        AnalysisJobEntity newEntity = AnalysisJobEntity.builder()
                                                       .document(doc)
                                                       .status(AnalysisStatus.QUEUED)
                                                       .build();
        when(mapper.toEntity(newJob, doc)).thenReturn(newEntity);

        AnalysisJobEntity savedEntity = AnalysisJobEntity.builder()
                                                         .document(doc)
                                                         .status(AnalysisStatus.QUEUED)
                                                         .build();
        savedEntity.setId(42L);
        when(analysisJobRepository.save(newEntity))
            .thenReturn(savedEntity);

        ExtractionJob expected = buildJob(42L);
        when(mapper.toDomain(savedEntity)).thenReturn(expected);

        ExtractionJob result = repository.save(newJob);

        assertThat(result.getJobId()).isEqualTo(42L);
        verify(documentRepository).findById(documentId);
    }

    @Test
    void shouldThrowWhenDocumentNotFound() {
        Long documentId = 99L;
        when(documentRepository.findById(documentId))
            .thenReturn(Optional.empty());

        ExtractionJob newJob = ExtractionJob.builder()
                                            .status(AnalysisStatus.QUEUED)
                                            .documentId(documentId)
                                            .originalFilename("test.pdf")
                                            .build();

        assertThatThrownBy(() -> repository.save(newJob))
            .isInstanceOf(EntityNotFoundException.class);
    }

    private AnalysisJobEntity buildEntity(Long id) {
        DocumentEntity doc = buildDocumentEntity(id);
        AnalysisJobEntity entity = AnalysisJobEntity.builder()
                                                    .document(doc)
                                                    .status(AnalysisStatus.QUEUED)
                                                    .build();
        entity.setId(id);
        return entity;
    }

    private DocumentEntity buildDocumentEntity(Long id) {
        DocumentEntity doc = DocumentEntity.builder()
                                           .originalFilename("test.pdf")
                                           .contentType("application/pdf")
                                           .fileSizeBytes(1000L)
                                           .storagePath("/tmp/test")
                                           .sha256Checksum("abc123")
                                           .build();
        doc.setId(id);
        return doc;
    }

    private ExtractionJob buildJob(Long jobId) {
        return ExtractionJob.builder()
                            .jobId(jobId)
                            .status(AnalysisStatus.QUEUED)
                            .submittedAt(Instant.now())
                            .originalFilename("test.pdf")
                            .build();
    }
}
