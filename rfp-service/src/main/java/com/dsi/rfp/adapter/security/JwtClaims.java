package com.dsi.rfp.adapter.security;

import com.dsi.rfp.domain.model.UserRole;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Set;

@Value
@Builder
public class JwtClaims {

    String subject;
    Set<UserRole> roles;
    Instant issuedAt;
    Instant expiresAt;
}
