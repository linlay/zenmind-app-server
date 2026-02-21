package com.app.auth.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.app.auth.config.AuthProperties;
import com.app.auth.service.JwkKeyService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/openid")
public class OpenIdController {

    private final AuthProperties authProperties;
    private final JwkKeyService jwkKeyService;

    public OpenIdController(AuthProperties authProperties, JwkKeyService jwkKeyService) {
        this.authProperties = authProperties;
        this.jwkKeyService = jwkKeyService;
    }

    @GetMapping("/.well-known/openid-configuration")
    public Map<String, Object> openidConfiguration() {
        String issuer = issuer();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", issuer);
        metadata.put("authorization_endpoint", issuer + "/oauth2/authorize");
        metadata.put("token_endpoint", issuer + "/oauth2/token");
        metadata.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post", "none"));
        metadata.put("jwks_uri", issuer + "/openid/jwks");
        metadata.put("userinfo_endpoint", issuer + "/openid/userinfo");
        metadata.put("revocation_endpoint", issuer + "/oauth2/revoke");
        metadata.put("introspection_endpoint", issuer + "/oauth2/introspect");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        metadata.put("subject_types_supported", List.of("public"));
        metadata.put("id_token_signing_alg_values_supported", List.of("RS256"));
        metadata.put("scopes_supported", List.of("openid", "profile"));
        metadata.put("claims_supported", List.of("sub", "preferred_username", "display_name", "scope"));
        return metadata;
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    public Map<String, Object> authorizationServerMetadata() {
        String issuer = issuer();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", issuer);
        metadata.put("authorization_endpoint", issuer + "/oauth2/authorize");
        metadata.put("token_endpoint", issuer + "/oauth2/token");
        metadata.put("jwks_uri", issuer + "/openid/jwks");
        metadata.put("revocation_endpoint", issuer + "/oauth2/revoke");
        metadata.put("introspection_endpoint", issuer + "/oauth2/introspect");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        return metadata;
    }

    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        return jwkKeyService.publicJwksResponse();
    }

    @GetMapping("/userinfo")
    public Map<String, Object> userinfoGet(@AuthenticationPrincipal Jwt jwt) {
        return userinfo(jwt);
    }

    @PostMapping("/userinfo")
    public Map<String, Object> userinfoPost(@AuthenticationPrincipal Jwt jwt) {
        return userinfo(jwt);
    }

    private Map<String, Object> userinfo(Jwt jwt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", jwt.getSubject());
        payload.put("preferred_username", jwt.getClaimAsString("preferred_username"));
        payload.put("display_name", jwt.getClaimAsString("display_name"));
        payload.put("scope", jwt.getClaimAsString("scope"));
        return payload;
    }

    private String issuer() {
        String issuer = authProperties.getIssuer();
        if (!StringUtils.hasText(issuer)) {
            throw new IllegalStateException("auth.issuer must be configured");
        }
        return issuer;
    }
}
