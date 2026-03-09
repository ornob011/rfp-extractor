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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;

@Service
public class AuthApplicationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final SecurityProperties securityProperties;
    private final Clock clock;

    public AuthApplicationService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokenService,
        SecurityProperties securityProperties,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.securityProperties = securityProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.username())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        guardPasswordMatches(
            request.password(),
            user.getPasswordHash()
        );

        String token = jwtTokenService.generateToken(
            user.getUsername(),
            Set.of(user.getRole())
        );

        JwtClaims claims = jwtTokenService.validateToken(token);

        return new LoginResponse(
            token,
            claims.getExpiresAt(),
            claims.getRoles()
        );
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        guardUsernameAvailable(request.username());

        UserEntity saved = userRepository.save(
            UserEntity.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.ANALYST)
                .enabled(true)
                .build()
        );

        return new SignupResponse(
            saved.getId(),
            saved.getUsername(),
            saved.getRole(),
            saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public LoginResponse refresh(String authorizationHeader) {
        JwtClaims claims = jwtTokenService.validateToken(extractBearerToken(authorizationHeader));
        guardRefreshWindow(claims);

        String token = jwtTokenService.generateToken(
            claims.getSubject(),
            claims.getRoles()
        );

        JwtClaims refreshed = jwtTokenService.validateToken(token);

        return new LoginResponse(
            token,
            refreshed.getExpiresAt(),
            refreshed.getRoles()
        );
    }

    private void guardPasswordMatches(
        String rawPassword,
        String passwordHash
    ) {
        if (passwordEncoder.matches(rawPassword, passwordHash)) {
            return;
        }

        throw new BadCredentialsException("Invalid credentials");
    }

    private void guardUsernameAvailable(String username) {
        if (!userRepository.existsByUsername(username)) {
            return;
        }

        throw new UsernameAlreadyExistsException(
            String.format("Username already taken: %s", username)
        );
    }

    private String extractBearerToken(String authorizationHeader) {
        String prefix = "Bearer ";

        if (authorizationHeader.startsWith(prefix)) {
            return authorizationHeader.substring(prefix.length());
        }

        throw new JwtValidationException("Authorization header must use Bearer token");
    }

    private void guardRefreshWindow(JwtClaims claims) {
        Duration refreshWindow = Duration.ofHours(securityProperties.auth().refreshWindowHours());
        Duration remaining = Duration.between(
            clock.instant(),
            claims.getExpiresAt()
        );

        if (remaining.compareTo(refreshWindow) <= 0) {
            return;
        }

        throw new JwtValidationException("Token is not yet eligible for refresh");
    }
}
