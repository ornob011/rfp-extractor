package com.dsi.rfp.adapter.rest.dto;

import com.dsi.rfp.domain.model.UserRole;

import java.time.Instant;
import java.util.Set;

public record LoginResponse(
    String token,
    Instant expiresAt,
    Set<UserRole> roles
) {
}
