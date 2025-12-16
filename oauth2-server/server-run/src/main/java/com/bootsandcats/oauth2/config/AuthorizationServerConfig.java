package com.bootsandcats.oauth2.config;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.bootsandcats.oauth2.log.AuthorizationDiagnosticsFilter;
import com.bootsandcats.oauth2.security.FederatedIdentityAuthenticationSuccessHandler;
import com.bootsandcats.oauth2.security.FormLoginDenyListSuccessHandler;
import com.bootsandcats.oauth2.service.JwkSetProvider;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import jakarta.servlet.http.HttpServletResponse;

/**
 * OAuth2 Authorization Server Security Configuration.
 *
 * <p>Configures OAuth2 authorization server with PKCE, OIDC, and JWT support.
 *
 * <p>WARNING: The default users and client secrets in this configuration are for demonstration
 * purposes only. In production environments, replace these with proper user management and load
 * secrets from environment variables or a secrets management system.
 */
@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationServerConfig.class);

    @Value("${oauth2.issuer-url:http://localhost:9000}")
    private String issuerUrl;

    @Value("${oauth2.demo-client-secret:demo-secret}")
    private String demoClientSecret;

    @Value("${oauth2.m2m-client-secret:m2m-secret}")
    private String m2mClientSecret;

    @Value("${oauth2.demo-user-password:password}")
    private String demoUserPassword;

    @Value("${oauth2.admin-user-password:admin}")
    private String adminUserPassword;

    /**
     * Security filter chain for OAuth2 Authorization Server endpoints.
     *
     * @param http HttpSecurity builder
     * @return Configured SecurityFilterChain
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http, SecurityHeadersConfig securityHeadersConfig) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(
                        authorizationServerConfigurer,
                        (authorizationServer) ->
                                authorizationServer
                                        .authorizationEndpoint(
                                                endpoint -> {
                                                    endpoint.errorResponseHandler(
                                                            authorizationErrorResponseHandler());
                                                    endpoint.authenticationProviders(
                                                            providers ->
                                                                    providers.forEach(
                                                                            provider -> {
                                                                                if (provider
                                                                                        instanceof
                                                                                        OAuth2AuthorizationCodeRequestAuthenticationProvider
                                                                                                        codeProvider) {
                                                                                    codeProvider
                                                                                            .setAuthenticationValidator(
                                                                                                    redirectUriPathOnlyValidator()
                                                                                                            .andThen(
                                                                                                                    OAuth2AuthorizationCodeRequestAuthenticationValidator
                                                                                                                            .DEFAULT_SCOPE_VALIDATOR));
                                                                                }
                                                                            }));
                                                })
                                        .oidc(
                                                oidc ->
                                                        oidc.providerConfigurationEndpoint(
                                                                providerConfig ->
                                                                        providerConfig
                                                                                .providerConfigurationCustomizer(
                                                                                        config ->
                                                                                                config
                                                                                                        .idTokenSigningAlgorithms(
                                                                                                                algs -> {
                                                                                                                    algs
                                                                                                                            .clear();
                                                                                                                    algs
                                                                                                                            .add(
                                                                                                                                    "ES256");
                                                                                                                })))))
                .csrf(
                        csrf ->
                                csrf.ignoringRequestMatchers(
                                        authorizationServerConfigurer.getEndpointsMatcher()))
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(
                                                "/.well-known/openid-configuration",
                                                "/.well-known/jwks.json",
                                                "/oauth2/jwks")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                // Redirect to the login page when not authenticated from the authorization endpoint
                .exceptionHandling(
                        (exceptions) ->
                                exceptions.defaultAuthenticationEntryPointFor(
                                        new LoginUrlAuthenticationEntryPoint("/login"),
                                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                // Accept access tokens for User Info and/or Client Registration
                .oauth2ResourceServer(
                        (resourceServer) -> resourceServer.jwt(Customizer.withDefaults()));

        http.addFilterBefore(
                new AuthorizationDiagnosticsFilter(), SecurityContextHolderFilter.class);

        securityHeadersConfig.configureSecurityHeaders(http);

        return http.build();
    }

    /**
     * Security filter chain for form login and default security.
     *
     * @param http HttpSecurity builder
     * @return Configured SecurityFilterChain
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(
            HttpSecurity http,
            FederatedIdentityAuthenticationSuccessHandler
                    federatedIdentityAuthenticationSuccessHandler,
            FormLoginDenyListSuccessHandler formLoginDenyListSuccessHandler,
            SecurityHeadersConfig securityHeadersConfig)
            throws Exception {
        http.authorizeHttpRequests(
                        (authorize) ->
                                authorize
                                        .requestMatchers(
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/actuator/info",
                                                "/actuator/prometheus",
                                                "/error",
                                                "/favicon.ico",
                                                "/css/**",
                                                "/js/**",
                                                "/docs/**",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html",
                                                "/assets/**",
                                                "/login",
                                                "/instances",
                                                "/instances/**",
                                                "/applications",
                                                "/applications/**",
                                                "/.well-known/jwks.json")
                                        .permitAll()
                                        .requestMatchers("/admin/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/admin/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/audit/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .authenticated())
                .formLogin(
                        form ->
                                form.loginPage("/login")
                                        .permitAll()
                                        .successHandler(formLoginDenyListSuccessHandler))
                .oauth2Login(
                        oauth2 ->
                                oauth2.loginPage("/login")
                                        .successHandler(
                                                federatedIdentityAuthenticationSuccessHandler))
                .csrf(
                        csrf ->
                                csrf.ignoringRequestMatchers(
                                        "/oauth2/token",
                                        "/oauth2/introspect",
                                        "/oauth2/revoke",
                                        "/instances",
                                        "/instances/**"));

        securityHeadersConfig.configureSecurityHeaders(http);

        return http.build();
    }

    /**
     * User details service for form login authentication.
     *
     * <p>WARNING: These are demo credentials only. In production, implement proper user management
     * with credentials loaded from a secure source (database, LDAP, etc.).
     *
     * @return UserDetailsService with default users
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails userDetails =
                User.builder()
                        .username("user")
                        .password(passwordEncoder.encode(demoUserPassword))
                        .roles("USER")
                        .build();

        UserDetails adminDetails =
                User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode(adminUserPassword))
                        .roles("USER", "ADMIN")
                        .build();

        return new InMemoryUserDetailsManager(userDetails, adminDetails);
    }

    /**
     * Password encoder using DelegatingPasswordEncoder to support {noop} and {bcrypt} prefixes.
     *
     * @return DelegatingPasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * JWK Source for JWT signing/verification.
     *
     * @return JWKSource with RSA key pair
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(JwkSetProvider jwkSetProvider) {
        return (selector, securityContext) -> selector.select(jwkSetProvider.getJwkSet());
    }

    /**
     * Customizes JWT tokens with ES256 algorithm and role-based profile scopes.
     *
     * <p>This customizer adds profile scopes to the token based on the user's role:
     *
     * <ul>
     *   <li>All authenticated users: profile:read, profile:write (for their own profile)
     *   <li>ADMIN role users: Additionally receive profile:admin scope
     * </ul>
     *
     * @return OAuth2TokenCustomizer for JWT encoding
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return context -> {
            // Set ES256 algorithm for all tokens
            context.getJwsHeader().algorithm(SignatureAlgorithm.ES256);

            // Only customize access tokens
            if (context.getTokenType().getValue().equals("access_token")) {
                var principal = context.getPrincipal();
                var claims = context.getClaims();

                // Get existing scopes from the token
                var existingScopes =
                        new java.util.HashSet<>(
                                claims.build().getClaimAsStringList("scope") != null
                                        ? claims.build().getClaimAsStringList("scope")
                                        : java.util.Collections.emptyList());

                // Add profile:read and profile:write for all authenticated users
                // This allows them to manage their own profile
                existingScopes.add("profile:read");
                existingScopes.add("profile:write");

                // Check if user has ADMIN role and add profile:admin scope
                if (principal != null && principal.getAuthorities() != null) {
                    boolean isAdmin =
                            principal.getAuthorities().stream()
                                    .anyMatch(
                                            auth ->
                                                    auth.getAuthority().equals("ROLE_ADMIN")
                                                            || auth.getAuthority()
                                                                    .equals("SCOPE_admin"));
                    if (isAdmin) {
                        existingScopes.add("profile:admin");
                        log.debug(
                                "Added profile:admin scope for admin user: {}",
                                principal.getName());
                    }
                }

                // Update the scope claim
                claims.claim("scope", existingScopes);
                log.debug(
                        "JWT customized with scopes: {} for user: {}",
                        existingScopes,
                        principal != null ? principal.getName() : "unknown");
            }
        };
    }

    /**
     * JWT Decoder for validating access tokens.
     *
     * @param jwkSource JWK source
     * @return JwtDecoder
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    /**
     * Authorization Server settings.
     *
     * @return AuthorizationServerSettings
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(issuerUrl)
                .authorizationEndpoint("/oauth2/authorize")
                .deviceAuthorizationEndpoint("/oauth2/device_authorization")
                .deviceVerificationEndpoint("/oauth2/device_verification")
                .tokenEndpoint("/oauth2/token")
                .jwkSetEndpoint("/oauth2/jwks")
                .tokenRevocationEndpoint("/oauth2/revoke")
                .tokenIntrospectionEndpoint("/oauth2/introspect")
                .oidcClientRegistrationEndpoint("/connect/register")
                .oidcUserInfoEndpoint("/userinfo")
                .oidcLogoutEndpoint("/connect/logout")
                .build();
    }

    private Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext>
            redirectUriPathOnlyValidator() {
        return authenticationContext -> {
            OAuth2AuthorizationCodeRequestAuthenticationToken
                    authorizationCodeRequestAuthentication =
                            authenticationContext.getAuthentication();
            RegisteredClient registeredClient = authenticationContext.getRegisteredClient();

            String requestedRedirectUri = authorizationCodeRequestAuthentication.getRedirectUri();

            if (StringUtils.hasText(requestedRedirectUri)) {
                UriComponents requestedRedirect = null;
                try {
                    requestedRedirect =
                            UriComponentsBuilder.fromUriString(requestedRedirectUri).build();
                } catch (Exception ex) {
                    log.debug("Failed to parse requested redirect URI", ex);
                }

                if (requestedRedirect == null || requestedRedirect.getFragment() != null) {
                    throwRedirectError(
                            OAuth2ErrorCodes.INVALID_REQUEST,
                            OAuth2ParameterNames.REDIRECT_URI,
                            authorizationCodeRequestAuthentication);
                }

                UriComponents requestedWithoutQuery =
                        UriComponentsBuilder.fromUriString(requestedRedirectUri)
                                .replaceQuery(null)
                                .fragment(null)
                                .build();
                Integer requestedPort = requestedRedirect.getPort();

                boolean validRedirect;
                if (!isLoopbackAddress(requestedRedirect.getHost())) {
                    validRedirect =
                            registeredClient.getRedirectUris().stream()
                                    .anyMatch(
                                            registeredUri ->
                                                    redirectWithoutQueryMatches(
                                                            registeredUri, requestedWithoutQuery));
                } else {
                    validRedirect =
                            registeredClient.getRedirectUris().stream()
                                    .anyMatch(
                                            registeredUri ->
                                                    redirectWithoutQueryMatches(
                                                            UriComponentsBuilder.fromUriString(
                                                                            registeredUri)
                                                                    .port(requestedPort)
                                                                    .replaceQuery(null)
                                                                    .fragment(null)
                                                                    .build(),
                                                            requestedWithoutQuery));
                }

                if (!validRedirect) {
                    throwRedirectError(
                            OAuth2ErrorCodes.INVALID_REQUEST,
                            OAuth2ParameterNames.REDIRECT_URI,
                            authorizationCodeRequestAuthentication);
                }

            } else {
                if (authorizationCodeRequestAuthentication.getScopes().contains(OidcScopes.OPENID)
                        || registeredClient.getRedirectUris().size() != 1) {
                    throwRedirectError(
                            OAuth2ErrorCodes.INVALID_REQUEST,
                            OAuth2ParameterNames.REDIRECT_URI,
                            authorizationCodeRequestAuthentication);
                }
            }
        };
    }

    private boolean redirectWithoutQueryMatches(
            String registeredRedirectUri, UriComponents requestedWithoutQuery) {
        try {
            UriComponents registered =
                    UriComponentsBuilder.fromUriString(registeredRedirectUri)
                            .replaceQuery(null)
                            .fragment(null)
                            .build();
            return redirectWithoutQueryMatches(registered, requestedWithoutQuery);
        } catch (Exception ex) {
            log.debug("Failed to parse registered redirect URI", ex);
            return false;
        }
    }

    private boolean redirectWithoutQueryMatches(
            UriComponents registeredWithoutQuery, UriComponents requestedWithoutQuery) {
        if (registeredWithoutQuery.getFragment() != null
                || requestedWithoutQuery.getFragment() != null) {
            return false;
        }

        String registeredPath = normalizePath(registeredWithoutQuery.getPath());
        String requestedPath = normalizePath(requestedWithoutQuery.getPath());

        return Objects.equals(registeredWithoutQuery.getScheme(), requestedWithoutQuery.getScheme())
                && Objects.equals(registeredWithoutQuery.getHost(), requestedWithoutQuery.getHost())
                && Objects.equals(registeredWithoutQuery.getPort(), requestedWithoutQuery.getPort())
                && Objects.equals(
                        registeredWithoutQuery.getUserInfo(), requestedWithoutQuery.getUserInfo())
                && Objects.equals(registeredPath, requestedPath);
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    private void throwRedirectError(
            String errorCode,
            String parameterName,
            OAuth2AuthorizationCodeRequestAuthenticationToken
                    authorizationCodeRequestAuthentication) {
        OAuth2Error error =
                new OAuth2Error(
                        errorCode,
                        "Invalid request: " + parameterName,
                        "https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1");
        throw new OAuth2AuthorizationCodeRequestAuthenticationException(
                error, authorizationCodeRequestAuthentication);
    }

    private boolean isLoopbackAddress(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        if ("[0:0:0:0:0:0:0:1]".equals(host) || "[::1]".equals(host)) {
            return true;
        }
        String[] ipv4Octets = host.split("\\.");
        if (ipv4Octets.length != 4) {
            return false;
        }
        try {
            int[] address = new int[ipv4Octets.length];
            for (int i = 0; i < ipv4Octets.length; i++) {
                address[i] = Integer.parseInt(ipv4Octets[i]);
            }
            return address[0] == 127
                    && address[1] >= 0
                    && address[1] <= 255
                    && address[2] >= 0
                    && address[2] <= 255
                    && address[3] >= 1
                    && address[3] <= 255;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private AuthenticationFailureHandler authorizationErrorResponseHandler() {
        return (request, response, exception) -> {
            try {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, exception.getMessage());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }
}
