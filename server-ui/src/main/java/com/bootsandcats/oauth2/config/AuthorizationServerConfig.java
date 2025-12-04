package com.bootsandcats.oauth2.config;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import com.bootsandcats.oauth2.security.FederatedIdentityAuthenticationSuccessHandler;
import com.bootsandcats.oauth2.service.JwkSetProvider;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

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
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http)
            throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(oidc -> oidc
                        .providerConfigurationEndpoint(providerConfig -> providerConfig
                                .providerConfigurationCustomizer(config -> config
                                        .idTokenSigningAlgorithm("ES256")))); // Advertise ES256 for ID tokens

        http
                // Redirect to the login page when not authenticated from the
                // authorization endpoint
                .exceptionHandling(
                        (exceptions) ->
                                exceptions.defaultAuthenticationEntryPointFor(
                                        new LoginUrlAuthenticationEntryPoint("/login"),
                                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                // Accept access tokens for User Info and/or Client Registration
                .oauth2ResourceServer(
                        (resourceServer) -> resourceServer.jwt(Customizer.withDefaults()));

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
                    federatedIdentityAuthenticationSuccessHandler)
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
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html",
                                                "/admin/**",
                                                "/assets/**",
                                                "/login",
                                                "/instances",
                                                "/instances/**",
                                                "/applications",
                                                "/applications/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .formLogin(Customizer.withDefaults())
                .oauth2Login(
                        oauth2 ->
                                oauth2.successHandler(
                                        federatedIdentityAuthenticationSuccessHandler))
                .csrf(
                        csrf ->
                                csrf.ignoringRequestMatchers(
                                        "/oauth2/token", "/oauth2/introspect", "/oauth2/revoke"));

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
     * Registered OAuth2 client repository.
     *
     * <p>WARNING: Client secrets shown here are defaults for demonstration. In production, load
     * secrets from environment variables (e.g., DEMO_CLIENT_SECRET, M2M_CLIENT_SECRET).
     *
     * @return RegisteredClientRepository with demo clients
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(
            JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        JdbcRegisteredClientRepository repository =
                new JdbcRegisteredClientRepository(jdbcTemplate);

        registerClientIfMissing(
                repository,
                "demo-client",
                () ->
                        buildConfidentialClient(
                                passwordEncoder.encode(demoClientSecret),
                                UUID.randomUUID().toString()));

        registerClientIfMissing(
                repository, "public-client", () -> buildPublicClient(UUID.randomUUID().toString()));

        registerClientIfMissing(
                repository,
                "m2m-client",
                () -> buildMachineToMachineClient(passwordEncoder.encode(m2mClientSecret)));

        return repository;
    }

    private void registerClientIfMissing(
            JdbcRegisteredClientRepository repository,
            String clientId,
            Supplier<RegisteredClient> supplier) {
        if (repository.findByClientId(clientId) == null) {
            log.info("Registering OAuth client '{}' in database", clientId);
            repository.save(supplier.get());
        }
    }

    private RegisteredClient buildConfidentialClient(String encodedSecret, String id) {
        return RegisteredClient.withId(id)
                .clientId("demo-client")
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .redirectUri("http://localhost:8080/callback")
                .redirectUri("http://127.0.0.1:8080/callback")
                .postLogoutRedirectUri("http://localhost:8080/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope("read")
                .scope("write")
                .tokenSettings(
                        TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(15))
                                .refreshTokenTimeToLive(Duration.ofDays(7))
                                .reuseRefreshTokens(false)
                                .build())
                .clientSettings(
                        ClientSettings.builder()
                                .requireAuthorizationConsent(true)
                                .requireProofKey(false)
                                .build())
                .build();
    }

    private RegisteredClient buildPublicClient(String id) {
        return RegisteredClient.withId(id)
                .clientId("public-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:3000/callback")
                .redirectUri("http://127.0.0.1:3000/callback")
                .postLogoutRedirectUri("http://localhost:3000/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope("read")
                .tokenSettings(
                        TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(15))
                                .refreshTokenTimeToLive(Duration.ofHours(24))
                                .reuseRefreshTokens(false)
                                .build())
                .clientSettings(
                        ClientSettings.builder()
                                .requireAuthorizationConsent(true)
                                .requireProofKey(true)
                                .build())
                .build();
    }

    private RegisteredClient buildMachineToMachineClient(String encodedSecret) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("m2m-client")
                .clientSecret(encodedSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("api:read")
                .scope("api:write")
                .tokenSettings(
                        TokenSettings.builder().accessTokenTimeToLive(Duration.ofHours(1)).build())
                .build();
    }

    /**
     * JWK Source for JWT signing/verification.
     *
     * <p>WARNING: The RSA key pair is generated dynamically on each server startup, which means
     * tokens issued before a restart will become invalid after restart. For production use,
     * consider persisting the JWK to a database or loading from a keystore to maintain token
     * validity across restarts.
     *
     * @return JWKSource with RSA key pair
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(JwkSetProvider jwkSetProvider) {
        return (selector, securityContext) -> selector.select(jwkSetProvider.getJwkSet());
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return context -> context.getJwsHeader().algorithm(SignatureAlgorithm.ES256);
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
}
