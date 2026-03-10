package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.persistence.entity.AnalysisJobEntity;
import com.dsi.rfp.adapter.persistence.entity.DocumentEntity;
import com.dsi.rfp.adapter.persistence.repository.AnalysisJobRepository;
import com.dsi.rfp.adapter.persistence.repository.DocumentRepository;
import com.dsi.rfp.adapter.persistence.repository.UserRepository;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.model.ExtractionJob;
import com.dsi.rfp.domain.port.out.JobStatePort;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@Transactional(readOnly = true)
public class JpaJobStateRepository implements JobStatePort {

    private final AnalysisJobRepository analysisJobRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AnalysisJobMapper mapper;

    public JpaJobStateRepository(
        AnalysisJobRepository analysisJobRepository,
        DocumentRepository documentRepository,
        UserRepository userRepository,
        AnalysisJobMapper mapper
    ) {
        this.analysisJobRepository = analysisJobRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ExtractionJob save(ExtractionJob job) {
        AnalysisJobEntity entity = resolveEntity(job);
        entity.setStatus(job.getStatus());
        entity.setProgressPercent(job.getProgress());
        entity.setErrorMessage(job.getErrorMessage());
        entity.setPageCount(job.getPageCount());
        entity.setSectionsJson(job.getSectionsJson());
        AnalysisJobEntity saved = analysisJobRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<ExtractionJob> findById(Long jobId) {
        return analysisJobRepository.findById(jobId)
                                    .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void updateStatus(Long jobId, AnalysisStatus status) {
        analysisJobRepository.findById(jobId)
                             .ifPresent(entity -> {
                                 entity.setStatus(status);
                                 switch (status) {
                                     case RUNNING -> entity.setStartedAt(Instant.now());
                                     case COMPLETED, FAILED -> entity.setCompletedAt(Instant.now());
                                     default -> log.warn(
                                         "event=unexpected.status component=JpaJobStateRepository method=updateStatus jobId={} status={}",
                                         jobId, status
                                     );
                                 }
                                 analysisJobRepository.save(entity);
                             });
    }

    @Override
    @Transactional
    public void updateProgress(Long jobId, int progress) {
        analysisJobRepository.findById(jobId)
                             .ifPresent(entity -> {
                                 entity.setProgressPercent(progress);
                                 analysisJobRepository.save(entity);
                             });
    }

    @Override
    @Transactional
    public void updateSectionsJson(Long jobId, JsonNode sectionsJson) {
        analysisJobRepository.findById(jobId)
                             .ifPresent(entity -> {
                                 entity.setSectionsJson(sectionsJson);
                                 analysisJobRepository.save(entity);
                             });
    }

    @Override
    public List<ExtractionJob> findAll() {
        return analysisJobRepository.findAll().stream()
                                    .map(mapper::toDomain)
                                    .toList();
    }

    private AnalysisJobEntity resolveEntity(ExtractionJob job) {
        return Optional.ofNullable(job.getJobId())
                       .flatMap(analysisJobRepository::findById)
                       .orElseGet(() -> newEntity(job));
    }

    private AnalysisJobEntity newEntity(ExtractionJob job) {
        DocumentEntity document = documentRepository
            .findById(job.getDocumentId())
            .orElseThrow(() -> new EntityNotFoundException(
                String.format(
                    "Document not found: %s", job.getDocumentId()
                )
            ));

        AnalysisJobEntity entity = mapper.toEntity(job, document);

        Optional.ofNullable(job.getSubmittedByUsername())
                .flatMap(userRepository::findByUsername)
                .ifPresent(entity::setSubmittedBy);

        return entity;
    }
}
