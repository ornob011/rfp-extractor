package com.dsi.rfp.application.service;

import com.dsi.rfp.adapter.persistence.entity.UserEntity;
import com.dsi.rfp.adapter.persistence.repository.UserRepository;
import com.dsi.rfp.adapter.rest.dto.LoginRequest;
import com.dsi.rfp.adapter.rest.dto.LoginResponse;
import com.dsi.rfp.adapter.rest.dto.SignupRequest;
import com.dsi.rfp.adapter.rest.dto.SignupResponse;
import com.dsi.rfp.adapter.security.JwtClaims;
import com.dsi.rfp.adapter.security.JwtTokenService;
import com.dsi.rfp.config.SecurityProperties;
import com.dsi.rfp.domain.exception.JwtValidationException;
import com.dsi.rfp.domain.exception.UsernameAlreadyExistsException;
import com.dsi.rfp.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    private AuthApplicationService service;

    @BeforeEach
    void setUp() {
        service = new AuthApplicationService(
            userRepository,
            passwordEncoder,
            jwtTokenService,
            new SecurityProperties(
                new SecurityProperties.Jwt(
                    "classpath:jwt-test-private.pem",
                    "classpath:jwt-test-public.pem",
                    8
                ),
                new SecurityProperties.Cors(List.of("http://localhost:5173")),
                new SecurityProperties.Auth(1)
            ),
            Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void shouldLoginSuccessfully() {
        UserEntity user = UserEntity.builder()
            .username("testuser")
            .passwordHash("hashed")
            .role(UserRole.ANALYST)
            .enabled(true)
            .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
        when(jwtTokenService.generateToken("testuser", Set.of(UserRole.ANALYST)))
            .thenReturn("jwt-token");
        when(jwtTokenService.validateToken("jwt-token")).thenReturn(
            JwtClaims.builder()
                .subject("testuser")
                .roles(Set.of(UserRole.ANALYST))
                .issuedAt(Instant.parse("2026-01-01T12:00:00Z"))
                .expiresAt(Instant.parse("2026-01-01T20:00:00Z"))
                .build()
        );

        LoginResponse response = service.login(new LoginRequest("testuser", "password"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.roles()).containsExactly(UserRole.ANALYST);
    }

    @Test
    void shouldRejectInvalidPassword() {
        UserEntity user = UserEntity.builder()
            .username("testuser")
            .passwordHash("hashed")
            .role(UserRole.ANALYST)
            .enabled(true)
            .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("testuser", "password")))
            .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldSignupAnalyst() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        SignupResponse response = service.signup(new SignupRequest("newuser", "password123"));

        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.role()).isEqualTo(UserRole.ANALYST);
    }

    @Test
    void shouldRejectDuplicateUsername() {
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        assertThatThrownBy(() -> service.signup(new SignupRequest("existing", "password123")))
            .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void shouldRefreshEligibleToken() {
        JwtClaims claims = JwtClaims.builder()
            .subject("testuser")
            .roles(Set.of(UserRole.ANALYST))
            .issuedAt(Instant.parse("2026-01-01T12:00:00Z"))
            .expiresAt(Instant.parse("2026-01-01T12:30:00Z"))
            .build();
        JwtClaims refreshed = JwtClaims.builder()
            .subject("testuser")
            .roles(Set.of(UserRole.ANALYST))
            .issuedAt(Instant.parse("2026-01-01T12:00:00Z"))
            .expiresAt(Instant.parse("2026-01-01T20:00:00Z"))
            .build();

        when(jwtTokenService.validateToken("old-token"))
            .thenReturn(claims);
        when(jwtTokenService.generateToken("testuser", Set.of(UserRole.ANALYST)))
            .thenReturn("new-token");
        when(jwtTokenService.validateToken("new-token"))
            .thenReturn(refreshed);

        LoginResponse response = service.refresh("Bearer old-token");

        assertThat(response.token()).isEqualTo("new-token");
        assertThat(response.expiresAt()).isEqualTo(refreshed.getExpiresAt());
    }

    @Test
    void shouldRejectRefreshOutsideWindow() {
        JwtClaims claims = JwtClaims.builder()
            .subject("testuser")
            .roles(Set.of(UserRole.ANALYST))
            .issuedAt(Instant.parse("2026-01-01T12:00:00Z"))
            .expiresAt(Instant.parse("2026-01-01T18:00:00Z"))
            .build();

        when(jwtTokenService.validateToken("old-token")).thenReturn(claims);

        assertThatThrownBy(() -> service.refresh("Bearer old-token"))
            .isInstanceOf(JwtValidationException.class)
            .hasMessageContaining("eligible for refresh");
    }
}
