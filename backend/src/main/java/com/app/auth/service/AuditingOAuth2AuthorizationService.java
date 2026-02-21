package com.app.auth.service;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
public class AuditingOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final RegisteredClientRepository registeredClientRepository;
    private final TokenAuditService tokenAuditService;

    public AuditingOAuth2AuthorizationService(
        OAuth2AuthorizationService delegate,
        RegisteredClientRepository registeredClientRepository,
        TokenAuditService tokenAuditService
    ) {
        this.delegate = delegate;
        this.registeredClientRepository = registeredClientRepository;
        this.tokenAuditService = tokenAuditService;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
        if (authorization == null) {
            return;
        }
        String clientId = resolveClientId(authorization.getRegisteredClientId());
        String authorizationId = authorization.getId();
        String username = authorization.getPrincipalName();

        OAuth2Authorization.Token<?> accessToken = authorization.getAccessToken();
        if (accessToken != null && accessToken.getToken() != null) {
            tokenAuditService.recordOAuthAccessToken(
                accessToken.getToken().getTokenValue(),
                username,
                clientId,
                authorizationId,
                accessToken.getToken().getIssuedAt(),
                accessToken.getToken().getExpiresAt()
            );
        }

        OAuth2Authorization.Token<?> refreshToken = authorization.getRefreshToken();
        if (refreshToken != null && refreshToken.getToken() != null) {
            tokenAuditService.recordOAuthRefreshToken(
                refreshToken.getToken().getTokenValue(),
                username,
                clientId,
                authorizationId,
                refreshToken.getToken().getIssuedAt(),
                refreshToken.getToken().getExpiresAt()
            );
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        if (authorization != null) {
            tokenAuditService.markRevokedByAuthorizationId(authorization.getId());
        }
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }

    private String resolveClientId(String registeredClientId) {
        if (registeredClientId == null) {
            return null;
        }
        RegisteredClient registeredClient = registeredClientRepository.findById(registeredClientId);
        return registeredClient == null ? registeredClientId : registeredClient.getClientId();
    }
}
