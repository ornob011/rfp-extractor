package com.dsi.rfp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    Jwt jwt,
    Cors cors,
    Auth auth
) {

    public record Jwt(
        String privateKeyPath,
        String publicKeyPath,
        int expiryHours
    ) {
    }

    public record Cors(
        List<String> allowedOrigins
    ) {
    }

    public record Auth(
        int refreshWindowHours
    ) {
    }
}
