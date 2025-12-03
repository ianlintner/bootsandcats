package com.bootsandcats.oauth2.config;

import com.bootsandcats.oauth2.security.FederatedIdentityAuthenticationSuccessHandler;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
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

    private final FederatedIdentityAuthenticationSuccessHandler federatedIdentityAuthenticationSuccessHandler;

    public AuthorizationServerConfig(FederatedIdentityAuthenticationSuccessHandler federatedIdentityAuthenticationSuccessHandler) {
        this.federatedIdentityAuthenticationSuccessHandler = federatedIdentityAuthenticationSuccessHandler;
    }

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
                .oidc(Customizer.withDefaults()); // Enable OpenID Connect 1.0

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
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
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
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(federatedIdentityAuthenticationSuccessHandler))
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
     * Password encoder using BCrypt.
     *
     * @return BCryptPasswordEncoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
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
    public RegisteredClientRepository registeredClientRepository(PasswordEncoder passwordEncoder) {
        // Confidential client with client_secret