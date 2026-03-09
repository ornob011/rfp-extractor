package com.dsi.rfp.application.service;

import com.dsi.rfp.domain.model.UserAuditEvent;
import com.dsi.rfp.domain.port.out.UserAuditPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserAuditService {

    private final UserAuditPort userAuditPort;

    public UserAuditService(UserAuditPort userAuditPort) {
        this.userAuditPort = userAuditPort;
    }

    public void record(UserAuditEvent event) {
        userAuditPort.record(event);
    }

    public List<UserAuditEvent> findByUsername(
        String username,
        int limit
    ) {
        return userAuditPort.findByUsername(username, limit);
    }

    public List<UserAuditEvent> findAll(int limit) {
        return userAuditPort.findAll(limit);
    }
}
