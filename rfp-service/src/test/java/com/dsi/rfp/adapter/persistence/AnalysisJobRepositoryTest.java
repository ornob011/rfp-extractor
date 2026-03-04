package com.dsi.rfp.adapter.persistence;

import com.dsi.rfp.adapter.persistence.entity.AnalysisJobEntity;
import com.dsi.rfp.adapter.persistence.entity.DocumentEntity;
import com.dsi.rfp.adapter.persistence.entity.UserEntity;
import com.dsi.rfp.adapter.persistence.repository.AnalysisJobRepository;
import com.dsi.rfp.adapter.persistence.repository.DocumentRepository;
import com.dsi.rfp.adapter.persistence.repository.UserRepository;
import com.dsi.rfp.config.JpaConfig;
import com.dsi.rfp.domain.model.AnalysisStatus;
import com.dsi.rfp.domain.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class AnalysisJobRepositoryTest {

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldFindByDocumentId() {
        DocumentEntity doc =
            documentRepository.save(
                DocumentEntity.builder()
                              .originalFilename("rfp.pdf")
                              .contentType("application/pdf")
                              .fileSizeBytes(2048L)
                              .storagePath("/tmp/rfp.pdf")
                              .sha256Checksum("sha-job-test")
                              .build());

        analysisJobRepository.save(
            AnalysisJobEntity.builder()
                             .document(doc)
                             .status(AnalysisStatus.QUEUED)
                             .progressPercent(0)
                             .build());

        List<AnalysisJobEntity> jobs = analysisJobRepository.findByDocumentId(doc.getId());

        assertThat(jobs).hasSize(1);
        assertThat(jobs.getFirst().getStatus()).isEqualTo(AnalysisStatus.QUEUED);
    }

    @Test
    void shouldFindAllByStatus() {
        DocumentEntity doc =
            documentRepository.save(
                DocumentEntity.builder()
                              .originalFilename("status-test.pdf")
                              .contentType("application/pdf")
                              .fileSizeBytes(1024L)
                              .storagePath("/tmp/status-test.pdf")
                              .sha256Checksum("sha-status-test")
                              .build());

        analysisJobRepository.save(
            AnalysisJobEntity.builder()
                             .document(doc)
                             .status(AnalysisStatus.QUEUED)
                             .progressPercent(0)
                             .build());

        analysisJobRepository.save(
            AnalysisJobEntity.builder()
                             .document(doc)
                             .status(AnalysisStatus.COMPLETED)
                             .progressPercent(100)
                             .build());

        List<AnalysisJobEntity> queued =
            analysisJobRepository.findAllByStatus(AnalysisStatus.QUEUED);

        assertThat(queued).hasSize(1);
        assertThat(queued.getFirst().getStatus()).isEqualTo(AnalysisStatus.QUEUED);
    }

    @Test
    void shouldFindBySubmittedByAndStatus() {
        UserEntity user =
            userRepository.save(
                UserEntity.builder()
                          .username("submitter1")
                          .passwordHash("hash")
                          .role(UserRole.ANALYST)
                          .enabled(true)
                          .build());

        DocumentEntity doc =
            documentRepository.save(
                DocumentEntity.builder()
                              .originalFilename("submitted.pdf")
                              .contentType("application/pdf")
                              .fileSizeBytes(3072L)
                              .storagePath("/tmp/submitted.pdf")
                              .sha256Checksum("sha-submitted-test")
                              .build());

        analysisJobRepository.save(
            AnalysisJobEntity.builder()
                             .document(doc)
                             .submittedBy(user)
                             .status(AnalysisStatus.RUNNING)
                             .progressPercent(50)
                             .build());

        List<AnalysisJobEntity> running =
            analysisJobRepository.findBySubmittedByAndStatus(user, AnalysisStatus.RUNNING);

        assertThat(running).hasSize(1);
    }
}
