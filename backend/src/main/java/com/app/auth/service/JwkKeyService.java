package com.app.auth.service;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class JwkKeyService {

    private final JdbcTemplate jdbcTemplate;

    public JwkKeyService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RSAKey loadOrCreate() {
        List<RSAKey> keys = jdbcTemplate.query(
            "SELECT KEY_ID_, PUBLIC_KEY_, PRIVATE_KEY_ FROM JWK_KEY_ ORDER BY CREATE_AT_ ASC LIMIT 1",
            (rs, rowNum) -> fromStored(
                rs.getString("KEY_ID_"),
                rs.getString("PUBLIC_KEY_"),
                rs.getString("PRIVATE_KEY_")
            )
        );

        if (!keys.isEmpty()) {
            return keys.getFirst();
        }

        RSAKey generated = generate();
        try {
            jdbcTemplate.update(
                "INSERT INTO JWK_KEY_(KEY_ID_, PUBLIC_KEY_, PRIVATE_KEY_, CREATE_AT_) VALUES (?, ?, ?, ?)",
                generated.getKeyID(),
                Base64.getEncoder().encodeToString(generated.toRSAPublicKey().getEncoded()),
                Base64.getEncoder().encodeToString(generated.toRSAPrivateKey().getEncoded()),
                Timestamp.from(Instant.now())
            );
        } catch (Exception ex) {
            throw new IllegalStateException("failed to store jwk key", ex);
        }
        return generated;
    }

    public Map<String, Object> publicJwksResponse() {
        RSAKey key = loadOrCreate();
        try {
            return Map.of(
                "keys", List.of(key.toPublicJWK().toJSONObject()),
                "publicKey", toPem(key.toRSAPublicKey().getEncoded())
            );
        } catch (JOSEException ex) {
            throw new IllegalStateException("failed to encode jwk public key", ex);
        }
    }

    private static RSAKey fromStored(String keyId, String publicKeyBase64, String privateKeyBase64) {
        try {
            byte[] publicBytes = Base64.getDecoder().decode(publicKeyBase64);
            byte[] privateBytes = Base64.getDecoder().decode(privateKeyBase64);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(publicBytes));
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load jwk key", ex);
        }
    }

    private static RSAKey generate() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to generate jwk key", ex);
        }
    }

    private static String toPem(byte[] encodedPublicKey) {
        String base64Body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
            .encodeToString(encodedPublicKey);
        return "-----BEGIN PUBLIC KEY-----\n" + base64Body + "\n-----END PUBLIC KEY-----";
    }
}
