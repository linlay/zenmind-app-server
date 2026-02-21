package com.app.auth.service;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.app.auth.config.AuthProperties;
import com.app.auth.security.AppPrincipal;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AppTokenService {

    private final AuthProperties authProperties;
    private final JwkKeyService jwkKeyService;

    public AppTokenService(AuthProperties authProperties, JwkKeyService jwkKeyService) {
        this.authProperties = authProperties;
        this.jwkKeyService = jwkKeyService;
    }

    public IssuedAccessToken issueAccessToken(String username, UUID deviceId) {
        return issueAccessToken(username, deviceId, authProperties.getApp().getAccessTtl());
    }

    public IssuedAccessToken issueAccessToken(String username, UUID deviceId, Duration accessTtl) {
        Objects.requireNonNull(accessTtl, "accessTtl");
        Instant now = Instant.now();
        Instant expireAt = now.plus(accessTtl);

        RSAKey key = jwkKeyService.loadOrCreate();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .issuer(authProperties.getIssuer())
            .subject(username)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expireAt))
            .claim("scope", "app")
            .claim("device_id", deviceId.toString())
            .build();

        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
            claimsSet
        );
        try {
            jwt.sign(new RSASSASigner(key.toRSAPrivateKey()));
        } catch (JOSEException ex) {
            throw new IllegalStateException("failed to sign access token", ex);
        }
        return new IssuedAccessToken(jwt.serialize(), now, expireAt);
    }

    public Optional<AppPrincipal> verify(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }

        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(token);
        } catch (ParseException ex) {
            return Optional.empty();
        }

        RSAKey key = jwkKeyService.loadOrCreate();
        boolean signatureOk;
        try {
            signatureOk = jwt.verify(new RSASSAVerifier(key.toRSAPublicKey()));
        } catch (JOSEException ex) {
            return Optional.empty();
        }
        if (!signatureOk) {
            return Optional.empty();
        }

        JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException ex) {
            return Optional.empty();
        }

        Date expireAt = claims.getExpirationTime();
        if (expireAt == null || expireAt.toInstant().isBefore(Instant.now())) {
            return Optional.empty();
        }

        String issuer = claims.getIssuer();
        if (!StringUtils.hasText(issuer) || !issuer.equals(authProperties.getIssuer())) {
            return Optional.empty();
        }

        String scope;
        String deviceIdText;
        try {
            scope = claims.getStringClaim("scope");
            deviceIdText = claims.getStringClaim("device_id");
        } catch (ParseException ex) {
            return Optional.empty();
        }
        if (!"app".equals(scope)) {
            return Optional.empty();
        }

        String subject = claims.getSubject();
        if (!StringUtils.hasText(subject) || !StringUtils.hasText(deviceIdText)) {
            return Optional.empty();
        }

        UUID deviceId;
        try {
            deviceId = UUID.fromString(deviceIdText);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        Instant issuedAt = claims.getIssueTime() == null ? Instant.now() : claims.getIssueTime().toInstant();
        return Optional.of(new AppPrincipal(subject, deviceId, issuedAt));
    }

    public record IssuedAccessToken(String token, Instant issuedAt, Instant expireAt) {
    }
}
