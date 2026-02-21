package com.app.auth.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.app.auth.config.AuthProperties;
import com.app.auth.config.CacheConfig;
import com.app.auth.domain.OAuthClient;
import com.app.auth.web.dto.ClientCreateRequest;
import com.app.auth.web.dto.ClientUpdateRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OAuthClientService {

    private static final RowMapper<OAuthClient> CLIENT_ROW_MAPPER = OAuthClientService::mapClient;

    private final JdbcTemplate jdbcTemplate;
    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    public OAuthClientService(
        JdbcTemplate jdbcTemplate,
        RegisteredClientRepository registeredClientRepository,
        PasswordEncoder passwordEncoder,
        AuthProperties authProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.registeredClientRepository = registeredClientRepository;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
    }

    public List<OAuthClient> listClients() {
        return jdbcTemplate.query(
            """
                SELECT ID_, CLIENT_ID_, CLIENT_NAME_, AUTH_GRANT_TYPES_, REDIRECT_URIS_, SCOPES_, REQUIRE_PKCE_, STATUS_, CREATE_AT_, UPDATE_AT_
                FROM OAUTH2_CLIENT_
                ORDER BY CREATE_AT_ DESC
            """,
            CLIENT_ROW_MAPPER
        );
    }

    @Cacheable(cacheNames = CacheConfig.CLIENT_BY_CLIENT_ID, key = "#clientId")
    public Optional<OAuthClient> findClient(String clientId) {
        List<OAuthClient> list = jdbcTemplate.query(
            """
                SELECT ID_, CLIENT_ID_, CLIENT_NAME_, AUTH_GRANT_TYPES_, REDIRECT_URIS_, SCOPES_, REQUIRE_PKCE_, STATUS_, CREATE_AT_, UPDATE_AT_
                FROM OAUTH2_CLIENT_
                WHERE CLIENT_ID_ = ?
            """,
            CLIENT_ROW_MAPPER,
            clientId
        );
        return list.stream().findFirst();
    }

    public OAuthClient createClient(ClientCreateRequest request) {
        validatePkceForAuthorizationCode(request.grantTypes(), request.requirePkce());

        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId(request.clientId())
            .clientIdIssuedAt(Instant.now())
            .clientName(request.clientName())
            .tokenSettings(defaultTokenSettings())
            .clientSettings(defaultClientSettings(Boolean.TRUE.equals(request.requirePkce())));

        String secret = StringUtils.hasText(request.clientSecret()) ? request.clientSecret().trim() : null;
        if (StringUtils.hasText(secret)) {
            builder.clientSecret(passwordEncoder.encode(secret));
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }

        request.grantTypes().stream().filter(StringUtils::hasText).forEach(grant ->
            builder.authorizationGrantType(new AuthorizationGrantType(grant.trim()))
        );

        if (request.redirectUris() != null) {
            request.redirectUris().stream().filter(StringUtils::hasText).forEach(uri ->
                builder.redirectUri(uri.trim())
            );
        }

        request.scopes().stream().filter(StringUtils::hasText).forEach(scope ->
            builder.scope(scope.trim())
        );

        registeredClientRepository.save(builder.build());

        String status = StringUtils.hasText(request.status()) ? request.status() : "ACTIVE";
        if (!"ACTIVE".equals(status)) {
            jdbcTemplate.update(
                "UPDATE OAUTH2_CLIENT_ SET STATUS_ = ?, UPDATE_AT_ = ? WHERE CLIENT_ID_ = ?",
                status,
                Timestamp.from(Instant.now()),
                request.clientId()
            );
        }

        evictClientCache(request.clientId());
        return findClient(request.clientId()).orElseThrow();
    }

    public OAuthClient updateClient(String clientId, ClientUpdateRequest request) {
        RegisteredClient existing = registeredClientRepository.findByClientId(clientId);
        if (existing == null) {
            throw new IllegalArgumentException("client not found or disabled");
        }

        boolean requirePkce = request.requirePkce() == null
            ? existing.getClientSettings().isRequireProofKey()
            : request.requirePkce();

        RegisteredClient.Builder builder = RegisteredClient.from(existing)
            .clientName(request.clientName())
            .clientSettings(defaultClientSettings(requirePkce))
            .tokenSettings(defaultTokenSettings());

        if (request.grantTypes() != null && !request.grantTypes().isEmpty()) {
            builder.authorizationGrantTypes(grants -> {
                grants.clear();
                request.grantTypes().stream().filter(StringUtils::hasText).forEach(grant ->
                    grants.add(new AuthorizationGrantType(grant.trim()))
                );
            });
        }

        if (request.redirectUris() != null) {
            builder.redirectUris(redirects -> {
                redirects.clear();
                request.redirectUris().stream().filter(StringUtils::hasText).forEach(uri ->
                    redirects.add(uri.trim())
                );
            });
        }

        if (request.scopes() != null && !request.scopes().isEmpty()) {
            builder.scopes(scopes -> {
                scopes.clear();
                request.scopes().stream().filter(StringUtils::hasText).forEach(scope ->
                    scopes.add(scope.trim())
                );
            });
        }

        validatePkceForAuthorizationCode(
            builder.build().getAuthorizationGrantTypes().stream().map(AuthorizationGrantType::getValue).toList(),
            requirePkce
        );

        registeredClientRepository.save(builder.build());

        if (StringUtils.hasText(request.status())) {
            jdbcTemplate.update(
                "UPDATE OAUTH2_CLIENT_ SET STATUS_ = ?, UPDATE_AT_ = ? WHERE CLIENT_ID_ = ?",
                request.status(),
                Timestamp.from(Instant.now()),
                clientId
            );
        }

        evictClientCache(clientId);
        return findClient(clientId).orElseThrow();
    }

    public OAuthClient patchStatus(String clientId, String status) {
        Optional<OAuthClient> current = findClient(clientId);
        if (current.isEmpty()) {
            throw new IllegalArgumentException("client not found");
        }

        jdbcTemplate.update(
            "UPDATE OAUTH2_CLIENT_ SET STATUS_ = ?, UPDATE_AT_ = ? WHERE CLIENT_ID_ = ?",
            status,
            Timestamp.from(Instant.now()),
            clientId
        );

        evictClientCache(clientId);
        return findClient(clientId).orElseThrow();
    }

    public String rotateSecret(String clientId) {
        Optional<OAuthClient> current = findClient(clientId);
        if (current.isEmpty()) {
            throw new IllegalArgumentException("client not found");
        }

        String rawSecret = UUID.randomUUID().toString().replace("-", "");
        String encoded = passwordEncoder.encode(rawSecret);

        jdbcTemplate.update(
            "UPDATE OAUTH2_CLIENT_ SET CLIENT_SECRET_ = ?, UPDATE_AT_ = ? WHERE CLIENT_ID_ = ?",
            encoded,
            Timestamp.from(Instant.now()),
            clientId
        );

        evictClientCache(clientId);
        return rawSecret;
    }

    public void ensureBootstrapClient() {
        String bootstrapClientId = "mobile-app";
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM OAUTH2_CLIENT_ WHERE CLIENT_ID_ = ?",
            Integer.class,
            bootstrapClientId
        );

        if (count != null && count > 0) {
            return;
        }

        ClientCreateRequest request = new ClientCreateRequest(
            bootstrapClientId,
            "Mobile App",
            null,
            List.of(AuthorizationGrantType.AUTHORIZATION_CODE.getValue(), AuthorizationGrantType.REFRESH_TOKEN.getValue()),
            List.of("myapp://oauthredirect"),
            List.of("openid", "profile"),
            true,
            "ACTIVE"
        );
        createClient(request);
    }

    @CacheEvict(cacheNames = CacheConfig.CLIENT_BY_CLIENT_ID, key = "#clientId")
    public void evictClientCache(String clientId) {
        // Cache eviction via annotation.
    }

    private TokenSettings defaultTokenSettings() {
        return TokenSettings.builder()
            .accessTokenTimeToLive(authProperties.getToken().getAccessTtl())
            .refreshTokenTimeToLive(authProperties.getToken().getRefreshTtl())
            .reuseRefreshTokens(!authProperties.getToken().isRotateRefreshToken())
            .build();
    }

    private ClientSettings defaultClientSettings(boolean requirePkce) {
        return ClientSettings.builder()
            .requireProofKey(requirePkce)
            .requireAuthorizationConsent(true)
            .build();
    }

    private static void validatePkceForAuthorizationCode(List<String> grantTypes, Boolean requirePkce) {
        boolean hasAuthCode = grantTypes != null && grantTypes.stream().filter(Objects::nonNull)
            .anyMatch(grantType -> AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(grantType.trim()));

        if (hasAuthCode && Boolean.FALSE.equals(requirePkce)) {
            throw new IllegalArgumentException("authorization_code clients must enable PKCE");
        }
    }

    private static OAuthClient mapClient(ResultSet rs, int rowNum) throws SQLException {
        return new OAuthClient(
            rs.getString("ID_"),
            rs.getString("CLIENT_ID_"),
            rs.getString("CLIENT_NAME_"),
            splitCsv(rs.getString("AUTH_GRANT_TYPES_")),
            splitCsv(rs.getString("REDIRECT_URIS_")),
            splitCsv(rs.getString("SCOPES_")),
            rs.getInt("REQUIRE_PKCE_") == 1,
            rs.getString("STATUS_"),
            rs.getTimestamp("CREATE_AT_").toInstant(),
            rs.getTimestamp("UPDATE_AT_").toInstant()
        );
    }

    private static List<String> splitCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .collect(Collectors.toCollection(ArrayList::new));
    }
}
