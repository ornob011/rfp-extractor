package com.dsi.rfp.adapter.security;

import com.dsi.rfp.config.SecurityProperties;
import com.dsi.rfp.domain.exception.JwtValidationException;
import com.dsi.rfp.domain.model.UserRole;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

@Slf4j
@Service
public class JwtTokenService {

    private final SecurityProperties securityProperties;
    private final ResourceLoader resourceLoader;
    private final Clock clock;

    private RSAPrivateKey privateKey;
    @Getter
    private RSAPublicKey publicKey;

    public JwtTokenService(
        SecurityProperties securityProperties,
        ResourceLoader resourceLoader,
        Clock clock
    ) {
        this.securityProperties = securityProperties;
        this.resourceLoader = resourceLoader;
        this.clock = clock;
    }

    @PostConstruct
    void loadKeys() {
        SecurityProperties.Jwt jwt = securityProperties.jwt();
        this.privateKey = loadPrivateKey(jwt.privateKeyPath());
        this.publicKey = loadPublicKey(jwt.publicKeyPath());

        log.info(
            "event=jwt.keys.loaded component=JwtTokenService algorithm=RS256"
        );
    }

    public String generateToken(
        String username,
        Set<UserRole> roles
    ) {
        Instant now = clock.instant();
        Instant expiry = now.plusSeconds((long) securityProperties.jwt().expiryHours() * 3600);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject(username)
            .claim(
                "roles",
                roles.stream().map(UserRole::name).toList()
            )
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiry))
            .build();

        SignedJWT signedJwt = new SignedJWT(
            new JWSHeader(JWSAlgorithm.RS256),
            claims
        );

        sign(signedJwt);

        return signedJwt.serialize();
    }

    public JwtClaims validateToken(String token) {
        SignedJWT signedJwt = parse(token);
        verify(signedJwt);

        JWTClaimsSet claims = extractClaims(signedJwt);

        return JwtClaims.builder()
            .subject(claims.getSubject())
            .roles(extractRoles(claims))
            .issuedAt(claims.getIssueTime().toInstant())
            .expiresAt(validateExpiry(claims))
            .build();
    }

    private void sign(SignedJWT jwt) {
        try {
            JWSSigner signer = new RSASSASigner(privateKey);
            jwt.sign(signer);
        } catch (JOSEException exception) {
            throw new JwtValidationException("Failed to sign JWT", exception);
        }
    }

    private SignedJWT parse(String token) {
        try {
            return SignedJWT.parse(token);
        } catch (ParseException exception) {
            throw new JwtValidationException("Invalid JWT format", exception);
        }
    }

    private void verify(SignedJWT jwt) {
        try {
            RSASSAVerifier verifier = new RSASSAVerifier(publicKey);
            boolean valid = jwt.verify(verifier);

            if (!valid) {
                throw new JwtValidationException("JWT signature verification failed");
            }
        } catch (JOSEException exception) {
            throw new JwtValidationException("JWT verification error", exception);
        }
    }

    private JWTClaimsSet extractClaims(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException exception) {
            throw new JwtValidationException("Failed to extract JWT claims", exception);
        }
    }

    private Set<UserRole> extractRoles(JWTClaimsSet claims) {
        try {
            return claims.getStringListClaim("roles")
                .stream()
                .map(UserRole::valueOf)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (ParseException exception) {
            throw new JwtValidationException("Failed to extract roles from JWT", exception);
        } catch (IllegalArgumentException exception) {
            throw new JwtValidationException("JWT contains invalid role", exception);
        }
    }

    private Instant validateExpiry(JWTClaimsSet claims) {
        Instant expiry = claims.getExpirationTime().toInstant();

        if (clock.instant().isAfter(expiry)) {
            throw new JwtValidationException("JWT has expired");
        }

        return expiry;
    }

    private RSAPrivateKey loadPrivateKey(String resourcePath) {
        try {
            return readPrivateKey(resourcePath);
        } catch (IOException exception) {
            throw new JwtValidationException(
                String.format("Failed to parse private key from: %s", resourcePath),
                exception
            );
        }
    }

    private RSAPublicKey loadPublicKey(String resourcePath) {
        try {
            return readPublicKey(resourcePath);
        } catch (IOException exception) {
            throw new JwtValidationException(
                String.format("Failed to parse public key from: %s", resourcePath),
                exception
            );
        }
    }

    private RSAPrivateKey readPrivateKey(String resourcePath) throws IOException {
        Resource resource = resourceLoader.getResource(resourcePath);
        return RsaKeyConverters.pkcs8().convert(resource.getInputStream());
    }

    private RSAPublicKey readPublicKey(String resourcePath) throws IOException {
        Resource resource = resourceLoader.getResource(resourcePath);
        return RsaKeyConverters.x509().convert(resource.getInputStream());
    }
}
