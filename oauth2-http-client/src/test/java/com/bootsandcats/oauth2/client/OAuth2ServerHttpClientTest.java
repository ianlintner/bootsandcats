package com.bootsandcats.oauth2.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import reactor.netty.http.client.HttpClient;

class OAuth2ServerHttpClientTest {

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void fetchUserInfoShouldCallEndpointWithBearerToken() throws InterruptedException {
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setBody("{\"sub\":\"123\",\"email\":\"user@example.com\"}"));

        OAuth2ServerHttpClient client = buildClient();

        Map<String, Object> response = client.fetchUserInfo("token-123");

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/userinfo");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token-123");
        assertThat(response.get("sub")).isEqualTo("123");
    }

    @Test
    void fetchHealthShouldReturnStatus() throws InterruptedException {
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setBody("{\"status\":\"UP\"}"));

        OAuth2ServerHttpClient client = buildClient();

        OAuth2ServerHealth health = client.fetchHealth();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/actuator/health");
        assertThat(health.status()).isEqualTo("UP");
    }

    private OAuth2ServerHttpClient buildClient() {
        OAuth2ServerClientProperties properties = new OAuth2ServerClientProperties();
        properties.setBaseUrl(mockWebServer.url("/").toString());

        WebClient webClient =
                WebClient.builder()
                        .clientConnector(
                                new ReactorClientHttpConnector(
                                        HttpClient.create()
                                                .responseTimeout(properties.getReadTimeout())
                                                .option(
                                                        io.netty.channel.ChannelOption
                                                                .CONNECT_TIMEOUT_MILLIS,
                                                        (int)
                                                                properties
                                                                        .getConnectTimeout()
                                                                        .toMillis())))
                        .baseUrl(properties.getBaseUrl())
                        .build();

        return new OAuth2ServerHttpClient(webClient, properties);
    }
}
