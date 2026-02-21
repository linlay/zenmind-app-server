package com.agw.auth.security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Component
public class CustomRegisteredClientRepository implements RegisteredClientRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CustomRegisteredClientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        ClassLoader classLoader = CustomRegisteredClientRepository.class.getClassLoader();
        List<Module> securityModules = SecurityJackson2Modules.getModules(classLoader);
        this.objectMapper.registerModules(securityModules);
        this.objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        Assert.notNull(registeredClient, "registeredClient cannot be null");

        String clientSettingsJson = toJson(registeredClient.getClientSettings().getSettings());
        String tokenSettingsJson = toJson(registeredClient.getTokenSettings().getSettings());
        int requirePkce = registeredClient.getClientSettings().isRequireProofKey() ? 1 : 0;

        if (existsById(registeredClient.getId())) {
            jdbcTemplate.update(
                """
                    UPDATE OAUTH2_CLIENT_
                    SET CLIENT_ID_ = ?, CLIENT_ID_ISSUED_AT_ = ?, CLIENT_SECRET_ = ?, CLIENT_SECRET_EXPIRES_AT_ = ?,
                        CLIENT_NAME_ = ?, CLIENT_AUTH_METHODS_ = ?, AUTH_GRANT_TYPES_ = ?, REDIRECT_URIS_ = ?,
                        POST_LOGOUT_REDIRECT_URIS_ = ?, SCOPES_ = ?, CLIENT_SETTINGS_ = ?, TOKEN_SETTINGS_ = ?, REQUIRE_PKCE_ = ?,
                        UPDATE_AT_ = CURRENT_TIMESTAMP
                    WHERE ID_ = ?
                """,
                registeredClient.getClientId(),
                toTimestamp(registeredClient.getClientIdIssuedAt()),
                registeredClient.getClientSecret(),
                toTimestamp(registeredClient.getClientSecretExpiresAt()),
                registeredClient.getClientName(),
                joinValues(registeredClient.getClientAuthenticationMethods().stream().map(ClientAuthenticationMethod::getValue).toList()),
                joinValues(registeredClient.getAuthorizationGrantTypes().stream().map(AuthorizationGrantType::getValue).toList()),
                joinValues(registeredClient.getRedirectUris().stream().toList()),
                joinValues(registeredClient.getPostLogoutRedirectUris().stream().toList()),
                joinValues(registeredClient.getScopes().stream().toList()),
                clientSettingsJson,
                tokenSettingsJson,
                requirePkce,
                registeredClient.getId()
            );
        } else {
            jdbcTemplate.update(
                """
                    INSERT INTO OAUTH2_CLIENT_(
                        ID_, CLIENT_ID_, CLIENT_ID_ISSUED_AT_, CLIENT_SECRET_, CLIENT_SECRET_EXPIRES_AT_, CLIENT_NAME_,
                        CLIENT_AUTH_METHODS_, AUTH_GRANT_TYPES_, REDIRECT_URIS_, POST_LOGOUT_REDIRECT_URIS_, SCOPES_,
                        CLIENT_SETTINGS_, TOKEN_SETTINGS_, REQUIRE_PKCE_, STATUS_, CREATE_AT_, UPDATE_AT_
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                registeredClient.getId(),
                registeredClient.getClientId(),
                toTimestamp(registeredClient.getClientIdIssuedAt()),
                registeredClient.getClientSecret(),
                toTimestamp(registeredClient.getClientSecretExpiresAt()),
                registeredClient.getClientName(),
                joinValues(registeredClient.getClientAuthenticationMethods().stream().map(ClientAuthenticationMethod::getValue).toList()),
                joinValues(registeredClient.getAuthorizationGrantTypes().stream().map(AuthorizationGrantType::getValue).toList()),
                joinValues(registeredClient.getRedirectUris().stream().toList()),
                joinValues(registeredClient.getPostLogoutRedirectUris().stream().toList()),
                joinValues(registeredClient.getScopes().stream().toList()),
                clientSettingsJson,
                tokenSettingsJson,
                requirePkce
            );
        }
    }

    @Override
    public RegisteredClient findById(String id) {
        List<RegisteredClient> list = jdbcTemplate.query(
            """
                SELECT ID_, CLIENT_ID_, CLIENT_ID_ISSUED_AT_, CLIENT_SECRET_, CLIENT_SECRET_EXPIRES_AT_, CLIENT_NAME_,
                       CLIENT_AUTH_METHODS_, AUTH_GRANT_TYPES_, REDIRECT_URIS_, POST_LOGOUT_REDIRECT_URIS_, SCOPES_,
                       CLIENT_SETTINGS_, TOKEN_SETTINGS_, STATUS_
                FROM OAUTH2_CLIENT_
                WHERE ID_ = ? AND STATUS_ = 'ACTIVE'
            """,
            this::map,
            id
        );

        return list.stream().findFirst().orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        List<RegisteredClient> list = jdbcTemplate.query(
            """
                SELECT ID_, CLIENT_ID_, CLIENT_ID_ISSUED_AT_, CLIENT_SECRET_, CLIENT_SECRET_EXPIRES_AT_, CLIENT_NAME_,
                       CLIENT_AUTH_METHODS_, AUTH_GRANT_TYPES_, REDIRECT_URIS_, POST_LOGOUT_REDIRECT_URIS_, SCOPES_,
                       CLIENT_SETTINGS_, TOKEN_SETTINGS_, STATUS_
                FROM OAUTH2_CLIENT_
                WHERE CLIENT_ID_ = ? AND STATUS_ = 'ACTIVE'
            """,
            this::map,
            clientId
        );

        return list.stream().findFirst().orElse(null);
    }

    private boolean existsById(String id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM OAUTH2_CLIENT_ WHERE ID_ = ?",
            Integer.class,
            id
        );
        return count != null && count > 0;
    }

    private RegisteredClient map(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> clientSettingsMap = fromJson(rs.getString("CLIENT_SETTINGS_"));
        Map<String, Object> tokenSettingsMap = fromJson(rs.getString("TOKEN_SETTINGS_"));

        RegisteredClient.Builder builder = RegisteredClient.withId(rs.getString("ID_"))
            .clientId(rs.getString("CLIENT_ID_"))
            .clientIdIssuedAt(toInstant(rs.getTimestamp("CLIENT_ID_ISSUED_AT_")))
            .clientSecret(rs.getString("CLIENT_SECRET_"))
            .clientSecretExpiresAt(toInstant(rs.getTimestamp("CLIENT_SECRET_EXPIRES_AT_")))
            .clientName(rs.getString("CLIENT_NAME_"))
            .clientSettings(ClientSettings.withSettings(clientSettingsMap).build())
            .tokenSettings(TokenSettings.withSettings(tokenSettingsMap).build());

        splitValues(rs.getString("CLIENT_AUTH_METHODS_")).forEach(value ->
            builder.clientAuthenticationMethod(new ClientAuthenticationMethod(value))
        );

        splitValues(rs.getString("AUTH_GRANT_TYPES_")).forEach(value ->
            builder.authorizationGrantType(new AuthorizationGrantType(value))
        );

        splitValues(rs.getString("REDIRECT_URIS_")).forEach(builder::redirectUri);
        splitValues(rs.getString("POST_LOGOUT_REDIRECT_URIS_")).forEach(builder::postLogoutRedirectUri);
        splitValues(rs.getString("SCOPES_")).forEach(builder::scope);

        return builder.build();
    }

    private String toJson(Map<String, Object> settings) {
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize settings", ex);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to parse settings", ex);
        }
    }

    private static String joinValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream().filter(StringUtils::hasText).collect(Collectors.joining(","));
    }

    private static List<String> splitValues(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .toList();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
