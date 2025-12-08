package com.bootsandcats.profileui.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bootsandcats.profileui.dto.ProfileListResponse;
import com.bootsandcats.profileui.dto.ProfileRequest;
import com.bootsandcats.profileui.dto.ProfileResponse;
import com.bootsandcats.profileui.exception.ProfileNotFoundException;
import com.bootsandcats.profileui.service.ProfileService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

@MicronautTest
class ProfileControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject ProfileService profileService;

    @MockBean(ProfileService.class)
    ProfileService mockProfileService() {
        return mock(ProfileService.class);
    }

    @Test
    void getMyProfile_whenProfileExists_returnsProfile() {
        ProfileResponse mockResponse = createTestResponse();
        when(profileService.getProfileBySubject(anyString())).thenReturn(mockResponse);

        HttpResponse<ProfileResponse> response =
                client.toBlocking()
                        .exchange(
                                HttpRequest.GET("/api/profile")
                                        .header("Authorization", "Bearer test-token"),
                                ProfileResponse.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.body()).isNotNull();
        assertThat(response.body().firstName()).isEqualTo("John");
    }

    @Test
    void getMyProfile_whenProfileNotExists_returns404() {
        when(profileService.getProfileBySubject(anyString()))
                .thenThrow(new ProfileNotFoundException("subject", "test"));

        try {
            client.toBlocking()
                    .exchange(
                            HttpRequest.GET("/api/profile")
                                    .header("Authorization", "Bearer test-token"),
                            ProfileResponse.class);
        } catch (HttpClientResponseException e) {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void createProfile_createsNewProfile() {
        ProfileRequest request = createTestRequest();
        ProfileResponse mockResponse = createTestResponse();
        when(profileService.createProfile(anyString(), anyString(), any(ProfileRequest.class)))
                .thenReturn(mockResponse);

        HttpResponse<ProfileResponse> response =
                client.toBlocking()
                        .exchange(
                                HttpRequest.POST("/api/profile", request)
                                        .header("Authorization", "Bearer test-token"),
                                ProfileResponse.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.body()).isNotNull();
        assertThat(response.body().firstName()).isEqualTo("John");
    }

    @Test
    void updateProfile_updatesExistingProfile() {
        ProfileRequest request = createTestRequest();
        ProfileResponse mockResponse = createTestResponse();
        when(profileService.updateProfile(anyString(), any(ProfileRequest.class)))
                .thenReturn(mockResponse);

        HttpResponse<ProfileResponse> response =
                client.toBlocking()
                        .exchange(
                                HttpRequest.PUT("/api/profile", request)
                                        .header("Authorization", "Bearer test-token"),
                                ProfileResponse.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.body()).isNotNull();
    }

    @Test
    void deleteProfile_deletesProfile() {
        doNothing().when(profileService).deleteProfile(anyString());

        HttpResponse<Void> response =
                client.toBlocking()
                        .exchange(
                                HttpRequest.DELETE("/api/profile")
                                        .header("Authorization", "Bearer test-token"),
                                Void.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(profileService).deleteProfile(anyString());
    }

    @Test
    void checkProfile_whenProfileExists_returnsExists() {
        when(profileService.profileExists(anyString())).thenReturn(true);

        HttpResponse<Map> response =
                client.toBlocking()
                        .exchange(
                                HttpRequest.GET("/api/profile/exists")
                                        .header("Authorization", "Bearer test-token"),
                                Map.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.body().get("exists")).isEqualTo(true);
    }

    @Test
    void checkProfile_whenProfileNotExists_returnsNotExists() {
        when(profileService.profileExists(anyString())).thenReturn(false);

        HttpResponse<Map> response =
                client.toBlocking()
                        .exchange(
                                HttpRequest.GET("/api/profile/exists")
                                        .header("Authorization", "Bearer test-token"),
                                Map.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.body().get("exists")).isEqualTo(false);
    }

    private ProfileResponse createTestResponse() {
        return new ProfileResponse(
                "test-id",
                "John",
                "Doe",
                "Johnny",
                "john@example.com",
                "555-0100",
                null,
                null,
                "Test bio",
                null,
                Instant.now(),
                Instant.now());
    }

    private ProfileRequest createTestRequest() {
        return new ProfileRequest(
                "John", "Doe", "Johnny", "john@example.com", "555-0100", "Test bio", null, null);
    }
}
