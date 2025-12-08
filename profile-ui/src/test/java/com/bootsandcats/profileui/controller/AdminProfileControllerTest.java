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
import java.util.List;
import org.junit.jupiter.api.Test;

@MicronautTest
class AdminProfileControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject ProfileService profileService;

    @MockBean(ProfileService.class)
    ProfileService mockProfileService() {
        return mock(ProfileService.class);
    }

    @Test
    void listProfiles_returnsPagedProfiles() {
        ProfileListResponse mockResponse =
                new ProfileListResponse(
                        List.of(createTestResponse()), 1L, 0, 10, 1);
        when(profileService.listProfiles(0, 10)).thenReturn(mockResponse);

        HttpResponse<ProfileListResponse> response =
                client.toBlocking()
                        .exchange(
                                HttpRequest.GET("/api/admin/profiles")
                                        .header("Authorization", "Bearer admin-token"),
                                ProfileListResponse.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.body()).isNotNull();
        assertThat(response.body().profiles()).hasSize(1);
        assertThat(response.body().totalCount()).isEqualTo(1);
    }

    @Test
    void listProfiles_withSearch_returnsFilteredProfiles() {
        ProfileListResponse mockResponse =
                new ProfileListResponse(
                        List.of(createTestResponse()), 1L, 0, 10, 1);
        when(profileService.searchProfiles("john", 0, 10)).thenReturn(mockResponse);

        HttpResponse<ProfileListResponse> response =
                client.toBlocking()
                        .exchange(
                                HttpRequest.GET("/api/admin/profiles?search=john")
                                        .header("Authorization", "Bearer admin-token"),
                                ProfileListResponse.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        verify(profileService).searchProfiles("john", 0, 10);
    }

    @Test
    void getProfileById_whenProfileExists_returnsProfile() {
        ProfileResponse mockResponse = createTestResponse();
        when(profileService.getProfileById("test-id")).thenReturn(mockResponse);

        HttpResponse<ProfileResponse> response =
                client.toBlocking()
                        .exchange(
                                HttpRequest.GET("/api/admin/profiles/test-id")
                                        .header("Authorization", "Bearer admin-token"),
                                ProfileResponse.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.body()).isNotNull();
        assertThat(response.body().firstName()).isEqualTo("John");
    }

    @Test
    void getProfileById_whenProfileNotExists_returns404() {
        when(profileService.getProfileById("nonexistent"))
                .thenThrow(new ProfileNotFoundException("id", "nonexistent"));

        try {
            client.toBlocking()
                    .exchange(
                            HttpRequest.GET("/api/admin/profiles/nonexistent")
                                    .header("Authorization", "Bearer admin-token"),
                            ProfileResponse.class);
        } catch (HttpClientResponseException e) {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void updateProfileById_updatesProfile() {
        ProfileRequest request = createTestRequest();
        ProfileResponse mockResponse = createTestResponse();
        when(profileService.updateProfileById(eq("test-id"), any(ProfileRequest.class)))
                .thenReturn(mockResponse);

        HttpResponse<ProfileResponse> response =
                client.toBlocking()
                        .exchange(
                                HttpRequest.PUT("/api/admin/profiles/test-id", request)
                                        .header("Authorization", "Bearer admin-token"),
                                ProfileResponse.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        verify(profileService).updateProfileById(eq("test-id"), any(ProfileRequest.class));
    }

    @Test
    void deleteProfileById_deletesProfile() {
        doNothing().when(profileService).deleteProfileById("test-id");

        HttpResponse<Void> response =
                client.toBlocking()
                        .exchange(
                                HttpRequest.DELETE("/api/admin/profiles/test-id")
                                        .header("Authorization", "Bearer admin-token"),
                                Void.class);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(profileService).deleteProfileById("test-id");
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
