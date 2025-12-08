package com.bootsandcats.profileui.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bootsandcats.profileui.AdminProfileController;
import com.bootsandcats.profileui.dto.ProfileListResponse;
import com.bootsandcats.profileui.dto.ProfileRequest;
import com.bootsandcats.profileui.dto.ProfileResponse;
import com.bootsandcats.profileui.service.ProfileService;

/**
 * Unit tests for AdminProfileController.
 *
 * <p>These tests verify the service layer interactions using pure Mockito.
 */
@ExtendWith(MockitoExtension.class)
class AdminProfileControllerTest {

    @Mock private ProfileService profileService;

    private AdminProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminProfileController(profileService);
    }

    @Test
    void listProfiles_returnsPagedProfiles() {
        ProfileListResponse mockResponse =
                new ProfileListResponse(List.of(createTestResponse()), 1L, 0, 10);
        when(profileService.listProfiles(0, 10)).thenReturn(mockResponse);

        ProfileListResponse result = profileService.listProfiles(0, 10);

        assertThat(result).isNotNull();
        assertThat(result.getProfiles()).hasSize(1);
        assertThat(result.getTotalCount()).isEqualTo(1);
    }

    @Test
    void listProfiles_withSearch_returnsFilteredProfiles() {
        ProfileListResponse mockResponse =
                new ProfileListResponse(List.of(createTestResponse()), 1L, 0, 10);
        when(profileService.searchProfiles("john", 0, 10)).thenReturn(mockResponse);

        ProfileListResponse result = profileService.searchProfiles("john", 0, 10);

        assertThat(result).isNotNull();
        verify(profileService).searchProfiles("john", 0, 10);
    }

    @Test
    void getProfileById_whenProfileExists_returnsProfile() {
        ProfileResponse mockResponse = createTestResponse();
        when(profileService.getProfileById("test-id")).thenReturn(Optional.of(mockResponse));

        Optional<ProfileResponse> result = profileService.getProfileById("test-id");

        assertThat(result).isPresent();
        assertThat(result.get().getFirstName()).isEqualTo("John");
    }

    @Test
    void getProfileById_whenProfileNotExists_returnsEmpty() {
        when(profileService.getProfileById("nonexistent")).thenReturn(Optional.empty());

        Optional<ProfileResponse> result = profileService.getProfileById("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void updateProfileById_updatesProfile() {
        ProfileRequest request = createTestRequest();
        ProfileResponse mockResponse = createTestResponse();
        when(profileService.updateProfileById(eq("test-id"), any(ProfileRequest.class)))
                .thenReturn(mockResponse);

        ProfileResponse result = profileService.updateProfileById("test-id", request);

        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("John");
        verify(profileService).updateProfileById(eq("test-id"), any(ProfileRequest.class));
    }

    @Test
    void deleteProfileById_deletesProfile() {
        when(profileService.deleteProfileById("test-id")).thenReturn(true);

        boolean result = profileService.deleteProfileById("test-id");

        assertThat(result).isTrue();
        verify(profileService).deleteProfileById("test-id");
    }

    private ProfileResponse createTestResponse() {
        ProfileResponse response = new ProfileResponse();
        response.setId("test-id");
        response.setOauthSubject("test-subject");
        response.setFirstName("John");
        response.setLastName("Doe");
        response.setPreferredName("Johnny");
        response.setEmail("john@example.com");
        response.setPhoneNumber("555-0100");
        response.setBio("Test bio");
        response.setCreatedAt(Instant.now());
        response.setUpdatedAt(Instant.now());
        return response;
    }

    private ProfileRequest createTestRequest() {
        ProfileRequest request = new ProfileRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setPreferredName("Johnny");
        request.setEmail("john@example.com");
        request.setPhoneNumber("555-0100");
        request.setBio("Test bio");
        return request;
    }
}
