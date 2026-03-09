package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.adapter.rest.dto.LoginRequest;
import com.dsi.rfp.adapter.rest.dto.LoginResponse;
import com.dsi.rfp.adapter.rest.dto.SignupRequest;
import com.dsi.rfp.adapter.rest.dto.SignupResponse;
import com.dsi.rfp.application.service.AuthApplicationService;
import com.dsi.rfp.domain.exception.UsernameAlreadyExistsException;
import com.dsi.rfp.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthApplicationService authApplicationService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authApplicationService);
    }

    @Test
    void shouldLoginSuccessfully() {
        when(authApplicationService.login(new LoginRequest("testuser", "password")))
            .thenReturn(new LoginResponse(
                "jwt-token",
                Instant.now().plusSeconds(3600),
                Set.of(UserRole.ANALYST)
            ));

        ResponseEntity<LoginResponse> response = controller.login(
            new LoginRequest("testuser", "password")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().token()).isEqualTo("jwt-token");
    }

    @Test
    void shouldRejectInvalidCredentials() {
        when(authApplicationService.login(new LoginRequest("testuser", "wrong")))
            .thenThrow(new BadCredentialsException("Invalid credentials"));

        assertThatThrownBy(() -> controller.login(
            new LoginRequest("testuser", "wrong")
        )).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void shouldSignupSuccessfully() {
        when(authApplicationService.signup(new SignupRequest("newuser", "password123")))
            .thenReturn(new SignupResponse(
                1L,
                "newuser",
                UserRole.ANALYST,
                Instant.now()
            ));

        ResponseEntity<SignupResponse> response = controller.signup(
            new SignupRequest("newuser", "password123")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().username()).isEqualTo("newuser");
        assertThat(response.getBody().role()).isEqualTo(UserRole.ANALYST);
    }

    @Test
    void shouldRejectDuplicateUsername() {
        when(authApplicationService.signup(new SignupRequest("existing", "password123")))
            .thenThrow(new UsernameAlreadyExistsException("Username already taken: existing"));

        assertThatThrownBy(() -> controller.signup(
            new SignupRequest("existing", "password123")
        )).isInstanceOf(UsernameAlreadyExistsException.class);
    }
}
