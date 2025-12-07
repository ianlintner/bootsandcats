package com.bootsandcats.profileui;

import static org.assertj.core.api.Assertions.assertThat;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
class HomeControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void rootEndpointResponds() {
        HttpResponse<String> response = client.toBlocking().exchange("/", String.class);
        assertThat(response.body()).contains("profile-ui");
        assertThat(response.getStatus().getCode()).isEqualTo(200);
    }
}
