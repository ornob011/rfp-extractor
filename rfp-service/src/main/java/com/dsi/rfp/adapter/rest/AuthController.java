package com.dsi.rfp.adapter.rest;

import com.dsi.rfp.adapter.rest.dto.LoginRequest;
import com.dsi.rfp.adapter.rest.dto.LoginResponse;
import com.dsi.rfp.adapter.rest.dto.SignupRequest;
import com.dsi.rfp.adapter.rest.dto.SignupResponse;
import com.dsi.rfp.application.service.AuthApplicationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthApplicationService authApplicationService;

    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(authApplicationService.login(request));
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
        @Valid @RequestBody SignupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                             .body(authApplicationService.signup(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
        @RequestHeader("Authorization") String authorizationHeader
    ) {
        return ResponseEntity.ok(
            authApplicationService.refresh(authorizationHeader)
        );
    }
}
