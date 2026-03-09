package com.dsi.rfp.adapter.persistence.repository;

import com.dsi.rfp.adapter.persistence.entity.UserAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAuditRepository extends JpaRepository<UserAuditEntity, Long> {

    List<UserAuditEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    List<UserAuditEntity> findAllByOrderByCreatedAtDesc();
}
