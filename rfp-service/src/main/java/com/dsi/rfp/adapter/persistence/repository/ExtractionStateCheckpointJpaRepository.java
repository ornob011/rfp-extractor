package com.dsi.rfp.adapter.persistence.repository;

import com.dsi.rfp.adapter.persistence.entity.ExtractionStateCheckpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface ExtractionStateCheckpointJpaRepository extends JpaRepository<ExtractionStateCheckpointEntity, Long> {

    Optional<ExtractionStateCheckpointEntity> findTopByJobIdOrderByCheckpointSequenceDesc(
        Long jobId
    );

    void deleteByJobId(Long jobId);

    void deleteByCreatedAtBefore(Instant cutoff);
}
