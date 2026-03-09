package com.dsi.rfp.domain.port.out;

import com.dsi.rfp.domain.model.UserAuditEvent;

import java.util.List;

public interface UserAuditPort {

    void record(UserAuditEvent event);

    List<UserAuditEvent> findByUsername(
        String username,
        int limit
    );

    List<UserAuditEvent> findAll(int limit);
}
