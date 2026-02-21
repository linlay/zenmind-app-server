package com.app.auth.config;

import java.time.Instant;
import java.util.List;

import com.app.auth.domain.AppUser;
import com.app.auth.security.AppApiAuthFilter;
import com.app.auth.service.AuditingOAuth2AuthorizationService;
import com.app.auth.service.AppUserService;
import com.app.auth.service.JwkKeyService;
import com.app.auth.service.OAuthClientService;
import com.app.auth.service.TokenAuditService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain authServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
            OAuth2AuthorizationServerConfigurer.authorizationServer();
        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

        http.securityMatcher(endpointsMatcher)
            .with(authorizationServerConfigurer, authorizationServer -> authorizationServer
                .authorizationEndpoint(endpoint -> endpoint.consentPage("/openid/consent"))
                .oidc(Customizer.withDefaults())
            )
            .exceptionHandling(exceptions ->
                exceptions.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/openid/login"))
            )
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher));

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain openIdUserInfoSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/openid/userinfo")
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain appApiSecurityFilterChain(HttpSecurity http, AppApiAuthFilter appApiAuthFilter) throws Exception {
        http.securityMatcher("/api/auth/**", "/api/app/**")
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
            .addFilterBefore(appApiAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain adminApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/admin/api/**")
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    @Order(5)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/openid/login",
                    "/openid/consent",
                    "/openid/.well-known/**",
                    "/error"
                ).permitAll()
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/openid/login")
                .loginProcessingUrl("/openid/login")
                .permitAll()
            )
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/openid/login", "/openid/consent")
            );

        return http.build();
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(AuthProperties authProperties) {
        return AuthorizationServerSettings.builder()
            .issuer(authProperties.getIssuer())
            .authorizationEndpoint("/oauth2/authorize")
            .tokenEndpoint("/oauth2/token")
            .tokenRevocationEndpoint("/oauth2/revoke")
            .tokenIntrospectionEndpoint("/oauth2/introspect")
            .jwkSetEndpoint("/openid/jwks")
            .build();
    }

    @Bean
    OAuth2AuthorizationService authorizationService(
        JdbcTemplate jdbcTemplate,
        RegisteredClientRepository registeredClientRepository,
        TokenAuditService tokenAuditService
    ) {
        OAuth2AuthorizationService delegate = new JdbcOAuth2AuthorizationService(
            jdbcTemplate,
            registeredClientRepository
        );
        return new AuditingOAuth2AuthorizationService(delegate, registeredClientRepository, tokenAuditService);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
        JdbcTemplate jdbcTemplate,
        RegisteredClientRepository registeredClientRepository
    ) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository);
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(JwkKeyService jwkKeyService) {
        RSAKey rsaKey = jwkKeyService.loadOrCreate();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AppUserService appUserService) {
        return username -> {
            AppUser appUser = appUserService.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            UserDetails user = User.withUsername(appUser.username())
                .password(appUser.passwordBcrypt())
                .authorities("ROLE_USER")
                .disabled(!appUser.isActive())
                .build();

            return user;
        };
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(AppUserService appUserService) {
        return context -> {
            String principalName = context.getPrincipal().getName();
            appUserService.findByUsername(principalName).ifPresent(appUser -> {
                context.getClaims().subject(appUser.userId().toString());
                context.getClaims().claim("preferred_username", appUser.username());
                context.getClaims().claim("display_name", appUser.displayName());
            });

            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                context.getClaims().audience(List.of("app-api"));
            }

            if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
                context.getClaims().claim("auth_time", Instant.now().getEpochSecond());
            }
        };
    }

    @Bean
    CommandLineRunner bootstrapData(
        AppUserService appUserService,
        OAuthClientService oAuthClientService,
        AuthProperties authProperties
    ) {
        return args -> {
            appUserService.ensureBootstrapUser(
                authProperties.getBootstrapUser().getUsername(),
                authProperties.getBootstrapUser().getPasswordBcrypt(),
                authProperties.getBootstrapUser().getDisplayName()
            );
            oAuthClientService.ensureBootstrapClient();
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Set-Cookie"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
