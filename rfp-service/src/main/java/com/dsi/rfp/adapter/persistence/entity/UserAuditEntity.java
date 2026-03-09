package com.dsi.rfp.adapter.persistence.entity;

import com.dsi.rfp.domain.model.AuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_audit_events")
@Getter
@Setter
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAuditEntity extends BaseEntity {

    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    private Long jobId;

    @Column(nullable = false)
    private Instant timestamp;

    private String ipAddress;

    @Column(nullable = false)
    private boolean success;
}
