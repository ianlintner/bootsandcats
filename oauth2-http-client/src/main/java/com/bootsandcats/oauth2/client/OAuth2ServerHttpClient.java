package com.bootsandcats.oauth2.client;

import java.time.Duration;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/** Lightweight HTTP client for interacting with the OAuth2 Authorization Server. */
public class OAuth2ServerHttpClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final OAuth2ServerClientProperties properties;

    public OAuth2ServerHttpClient(WebClient webClient, OAuth2ServerClientProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    /**
     * Calls the {@code /userinfo} endpoint using the supplied access token.
     *
     * @param accessToken the bearer token to use
     * @return a map of claims returned by the userinfo endpoint
     */
    public Map<String, Object> fetchUserInfo(String accessToken) {
        return webClient
                .get()
                .uri(properties.getUserInfoPath())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> r.createException().flatMap(Mono::error))
                .bodyToMono(MAP_TYPE)
                .block(timeout());
    }

    /**
     * Calls the {@code /actuator/health} endpoint to retrieve a simple health status.
     *
     * @return {@link OAuth2ServerHealth} with status and optional details
     */
    public OAuth2ServerHealth fetchHealth() {
        return webClient
                .get()
                .uri(properties.getHealthPath())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, r -> r.createException().flatMap(Mono::error))
                .bodyToMono(OAuth2ServerHealth.class)
                .block(timeout());
    }

    private Duration timeout() {
        return properties.getReadTimeout();
    }
}
