package com.bootsandcats.profileui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

@MicronautTest
@io.micronaut.context.annotation.Property(name = "micronaut.security.enabled", value = "false")
class HomeControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void rootEndpointServesThemedLandingPage() {
        HttpResponse<String> response = client.toBlocking().exchange("/", String.class);

        assertThat(response.getStatus().getCode()).isEqualTo(200);
        assertThat(response.body()).contains("Profile UI").contains("@ianlintner/theme");
    }

    @Test
    void apiStatusReturnsJson() {
        HttpResponse<String> response = client.toBlocking().exchange("/api/status", String.class);

        assertThat(response.getStatus().getCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"service\":\"profile-ui\"");
    }
}
