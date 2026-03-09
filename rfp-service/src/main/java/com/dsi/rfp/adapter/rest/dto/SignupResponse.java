package com.dsi.rfp.adapter.rest.dto;

import com.dsi.rfp.domain.model.UserRole;

import java.time.Instant;

public record SignupResponse(
    Long userId,
    String username,
    UserRole role,
    Instant createdAt
) {
}
