package com.dsi.rfp.config;

import com.dsi.rfp.adapter.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private JwtTokenService jwtTokenService;

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        lenient().when(jwtTokenService.getPublicKey()).thenReturn(publicKey);
        securityConfig = new SecurityConfig(
            jwtTokenService,
            new SecurityProperties(
                new SecurityProperties.Jwt("classpath:jwt-test-private.pem", "classpath:jwt-test-public.pem", 1),
                new SecurityProperties.Cors(List.of("http://localhost:5173")),
                new SecurityProperties.Auth(1)
            )
        );
    }

    @Test
    void shouldCreateJwtDecoderBean() {
        JwtDecoder decoder = securityConfig.jwtDecoder();
        assertThat(decoder).isNotNull();
    }

    @Test
    void shouldCreateJwtAuthenticationConverterWithRolesClaim() {
        JwtAuthenticationConverter converter =
            securityConfig.jwtAuthenticationConverter();
        assertThat(converter).isNotNull();
    }

    @Test
    void shouldCreateBCryptPasswordEncoder() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test
    void shouldCreateCorsConfigurationSource() {
        CorsConfigurationSource source =
            securityConfig.corsConfigurationSource();
        assertThat(source).isNotNull();
    }

    @Test
    void shouldEncodeAndVerifyPassword() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String raw = "testPassword123";
        String encoded = encoder.encode(raw);

        assertThat(encoder.matches(raw, encoded)).isTrue();
        assertThat(encoder.matches("wrong", encoded)).isFalse();
    }
}
