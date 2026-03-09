package com.dsi.rfp.adapter.persistence.repository;

import com.dsi.rfp.adapter.persistence.entity.AnalysisJobEntity;
import com.dsi.rfp.adapter.persistence.entity.UserEntity;
import com.dsi.rfp.domain.model.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJobEntity, Long> {

    List<AnalysisJobEntity> findByDocumentId(Long documentId);

    List<AnalysisJobEntity> findBySubmittedByAndStatus(
        UserEntity submittedBy, AnalysisStatus status
    );

    List<AnalysisJobEntity> findAllByStatus(AnalysisStatus status);

    List<AnalysisJobEntity> findBySubmittedByUsername(String username);

    List<AnalysisJobEntity> findByStatusAndCompletedAtBefore(
        AnalysisStatus status,
        Instant before
    );
}
