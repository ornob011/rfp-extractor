package com.dsi.rfp.adapter.security;

import com.dsi.rfp.config.SecurityProperties;
import com.dsi.rfp.domain.exception.JwtValidationException;
import com.dsi.rfp.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private JwtTokenService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        service = new JwtTokenService(
            securityProperties(8),
            new DefaultResourceLoader(),
            clock
        );
        service.loadKeys();
    }

    @Test
    void shouldGenerateAndValidateToken() {
        String token = service.generateToken("testuser", Set.of(UserRole.ANALYST));
        JwtClaims claims = service.validateToken(token);

        assertThat(claims.getSubject()).isEqualTo("testuser");
        assertThat(claims.getRoles()).contains(UserRole.ANALYST);
    }

    @Test
    void shouldRejectExpiredToken() {
        Clock pastClock = Clock.fixed(
            Instant.parse("2020-01-01T00:00:00Z"),
            ZoneOffset.UTC
        );
        JwtTokenService pastService = new JwtTokenService(
            securityProperties(1),
            new DefaultResourceLoader(),
            pastClock
        );
        pastService.loadKeys();

        String token = pastService.generateToken("user", Set.of(UserRole.ANALYST));

        assertThatThrownBy(() -> service.validateToken(token))
            .isInstanceOf(JwtValidationException.class)
            .hasMessageContaining("expired");
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = service.generateToken("user", Set.of(UserRole.ANALYST));
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> service.validateToken(tampered))
            .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shouldRejectInvalidFormat() {
        assertThatThrownBy(() -> service.validateToken("not.a.jwt"))
            .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shouldIncludeMultipleRoles() {
        Set<UserRole> roles = Set.of(UserRole.ADMIN, UserRole.ANALYST);
        String token = service.generateToken("admin", roles);
        JwtClaims claims = service.validateToken(token);

        assertThat(claims.getRoles()).containsExactlyInAnyOrder(
            UserRole.ADMIN,
            UserRole.ANALYST
        );
    }

    private SecurityProperties securityProperties(int expiryHours) {
        return new SecurityProperties(
            new SecurityProperties.Jwt(
                "classpath:jwt-test-private.pem",
                "classpath:jwt-test-public.pem",
                expiryHours
            ),
            new SecurityProperties.Cors(java.util.List.of("http://localhost:5173")),
            new SecurityProperties.Auth(1)
        );
    }
}
