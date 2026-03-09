package com.dsi.rfp.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class UserAuditEvent {

    Long eventId;
    String username;
    AuditAction action;
    Long jobId;
    Instant timestamp;
    String ipAddress;
    boolean success;
}
