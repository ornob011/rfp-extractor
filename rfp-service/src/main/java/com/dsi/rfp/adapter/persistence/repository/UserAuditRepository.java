package com.dsi.rfp.adapter.persistence.repository;

import com.dsi.rfp.adapter.persistence.entity.UserAuditEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAuditRepository extends JpaRepository<UserAuditEntity, Long> {

    Page<UserAuditEntity> findByUsernameOrderByCreatedAtDesc(
        String username,
        Pageable pageable
    );

    Page<UserAuditEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
