package com.dsi.rfp.adapter.persistence.repository;

import com.dsi.rfp.adapter.persistence.entity.DocumentEntity;
import com.dsi.rfp.adapter.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    Optional<DocumentEntity> findBySha256Checksum(String sha256Checksum);

    List<DocumentEntity> findByUploadedBy(UserEntity uploadedBy);
}
