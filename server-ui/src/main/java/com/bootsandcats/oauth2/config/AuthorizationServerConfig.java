package com.bootsandcats.oauth2.config;

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
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import com.bootsandcats.oauth2.log.AuthorizationDiagnosticsFilter;
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
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(
                        authorizationServerConfigurer,
                        (authorizationServer) ->
                                authorizationServer.oidc(
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
                                                "/docs/**",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html",
                                                "/admin/**",
                                                "/assets/**",
                                                "/login",
                                                "/instances",
                                                "/instances/**",
                                                "/applications",
                                                "/applications/**",
                                                "/.well-known/jwks.json")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .formLogin(form -> form.loginPage("/login").permitAll())
                .oauth2Login(
                        oauth2 ->
                                oauth2.successHandler(
                                        federatedIdentityAuthenticationSuccessHandler))
                .csrf(
                        csrf ->
                                csrf.ignoringRequestMatchers(
                                        "/oauth2/token",
                                        "/oauth2/introspect",
                                        "/oauth2/revoke",
                                        "/instances",
                                        "/instances/**"));

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
    /* Removed registeredClientRepository bean and helper methods */

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
