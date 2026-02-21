package com.app.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSecurityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM TOKEN_AUDIT_");
        jdbcTemplate.update("DELETE FROM oauth2_authorization");
        jdbcTemplate.update("DELETE FROM DEVICE_");
    }

    @Test
    void adminSecurityApisShouldRequireSession() throws Exception {
        mockMvc.perform(get("/admin/api/security/tokens"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/admin/api/security/public-key/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"e\":\"AQAB\",\"n\":\"abc\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/admin/api/security/key-pair/generate"))
            .andExpect(status().isUnauthorized());

        MockCookie cookie = adminLoginCookie();
        mockMvc.perform(get("/admin/api/security/jwks").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jwks.keys[0].kty").value("RSA"));
    }

    @Test
    void generatePublicKeyAndKeyPairShouldWork() throws Exception {
        MockCookie cookie = adminLoginCookie();
        MvcResult jwksResult = mockMvc.perform(get("/admin/api/security/jwks").cookie(cookie))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode jwk = readJson(jwksResult).path("jwks").path("keys").path(0);
        String e = jwk.path("e").asText();
        String n = jwk.path("n").asText();

        mockMvc.perform(post("/admin/api/security/public-key/generate")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"e\":\"" + e + "\",\"n\":\"" + n + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publicKey").value(Matchers.startsWith("-----BEGIN PUBLIC KEY-----")));

        mockMvc.perform(post("/admin/api/security/key-pair/generate")
                .cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publicKey").value(Matchers.startsWith("-----BEGIN PUBLIC KEY-----")))
            .andExpect(jsonPath("$.privateKey").value(Matchers.startsWith("-----BEGIN PRIVATE KEY-----")));
    }

    @Test
    void generatePublicKeyShouldValidateInput() throws Exception {
        MockCookie cookie = adminLoginCookie();
        mockMvc.perform(post("/admin/api/security/public-key/generate")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"e\":\"%%%\",\"n\":\"%%%\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void issueRefreshAndRevokeShouldBeAudited() throws Exception {
        MockCookie cookie = adminLoginCookie();
        MvcResult issue = mockMvc.perform(post("/admin/api/security/app-tokens/issue")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "masterPassword":"password",
                      "deviceName":"Admin Console Device",
                      "accessTtlSeconds":900
                    }
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

        JsonNode issueJson = readJson(issue);
        String deviceToken = issueJson.path("deviceToken").asText();
        String deviceId = issueJson.path("deviceId").asText();

        mockMvc.perform(post("/admin/api/security/app-tokens/refresh")
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"deviceToken\":\"" + deviceToken + "\",\"accessTtlSeconds\":600}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(get("/admin/api/security/tokens?sources=APP_ACCESS&status=ACTIVE&limit=50")
                .cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].source").value("APP_ACCESS"))
            .andExpect(jsonPath("$[0].token").isNotEmpty())
            .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        mockMvc.perform(post("/admin/api/security/app-devices/" + deviceId + "/revoke")
                .cookie(cookie))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/admin/api/security/tokens?sources=APP_ACCESS&status=REVOKED&limit=50")
                .cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].deviceId").value(deviceId))
            .andExpect(jsonPath("$[0].status").value("REVOKED"));
    }

    @Test
    void oauthAuthorizationSaveAndRemoveShouldBeAudited() {
        RegisteredClient client = registeredClientRepository.findByClientId("mobile-app");
        org.assertj.core.api.Assertions.assertThat(client).isNotNull();

        Instant now = Instant.now();
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
            .id(UUID.randomUUID().toString())
            .principalName("user")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .token(new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "oauth-access-token-" + UUID.randomUUID(),
                now,
                now.plusSeconds(900),
                Set.of("openid", "profile")
            ))
            .token(new OAuth2RefreshToken(
                "oauth-refresh-token-" + UUID.randomUUID(),
                now,
                now.plusSeconds(3600)
            ))
            .build();

        authorizationService.save(authorization);

        Long activeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM TOKEN_AUDIT_ WHERE AUTHORIZATION_ID_ = ? AND SOURCE_ IN ('OAUTH_ACCESS','OAUTH_REFRESH')",
            Long.class,
            authorization.getId()
        );
        org.assertj.core.api.Assertions.assertThat(activeCount).isEqualTo(2);

        authorizationService.remove(authorization);
        Long revokedCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM TOKEN_AUDIT_ WHERE AUTHORIZATION_ID_ = ? AND REVOKED_AT_ IS NOT NULL",
            Long.class,
            authorization.getId()
        );
        org.assertj.core.api.Assertions.assertThat(revokedCount).isEqualTo(2);
    }

    @Test
    void listTokensShouldCleanupRecordsOlderThan30Days() throws Exception {
        Instant oldIssuedAt = Instant.now().minusSeconds(40L * 24L * 3600L);
        jdbcTemplate.update(
            """
                INSERT INTO TOKEN_AUDIT_ (
                    TOKEN_ID_, SOURCE_, TOKEN_VALUE_, TOKEN_SHA256_, USERNAME_, DEVICE_ID_, DEVICE_NAME_, CLIENT_ID_, AUTHORIZATION_ID_,
                    ISSUED_AT_, EXPIRES_AT_, REVOKED_AT_, CREATE_AT_, UPDATE_AT_
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID().toString(),
            "APP_ACCESS",
            "legacy-token",
            "legacy-sha256",
            "app",
            null,
            null,
            null,
            null,
            Timestamp.from(oldIssuedAt),
            Timestamp.from(oldIssuedAt.plusSeconds(600)),
            null,
            Timestamp.from(oldIssuedAt),
            Timestamp.from(oldIssuedAt)
        );

        MockCookie cookie = adminLoginCookie();
        mockMvc.perform(get("/admin/api/security/tokens?sources=APP_ACCESS&status=ALL&limit=50")
                .cookie(cookie))
            .andExpect(status().isOk());

        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM TOKEN_AUDIT_ WHERE TOKEN_SHA256_ = 'legacy-sha256'",
            Long.class
        );
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(0);
    }

    private MockCookie adminLoginCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/api/session/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username":"admin",
                      "password":"password"
                    }
                """))
            .andExpect(status().isOk())
            .andReturn();
        MockCookie cookie = (MockCookie) result.getResponse().getCookie("ADMIN_SESSION");
        if (cookie == null) {
            throw new IllegalStateException("missing admin cookie");
        }
        return cookie;
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
