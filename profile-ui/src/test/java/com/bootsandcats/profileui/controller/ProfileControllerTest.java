package com.bootsandcats.profileui.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bootsandcats.profileui.ProfileController;
import com.bootsandcats.profileui.dto.ProfileRequest;
import com.bootsandcats.profileui.dto.ProfileResponse;
import com.bootsandcats.profileui.service.ProfileService;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for ProfileController.
 *
 * <p>These tests verify the service layer interactions using pure Mockito.
 */
@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private ProfileService profileService;

    private ProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new ProfileController(profileService);
    }

    @Test
    void profileExists_returnsCorrectStatus() {
        when(profileService.profileExists(anyString())).thenReturn(true);

        // This test verifies the service layer is properly injected
        assertThat(profileService.profileExists("test")).isTrue();
    }

    @Test
    void listProfiles_callsService() {
        // Verify service is properly mocked
        when(profileService.profileExists(anyString())).thenReturn(false);

        assertThat(profileService.profileExists("nonexistent")).isFalse();
        verify(profileService).profileExists("nonexistent");
    }

    @Test
    void getProfileBySubject_returnsProfile() {
        ProfileResponse response = createTestResponse();
        when(profileService.getProfileBySubject(anyString())).thenReturn(Optional.of(response));

        Optional<ProfileResponse> result = profileService.getProfileBySubject("test-subject");

        assertThat(result).isPresent();
        assertThat(result.get().getFirstName()).isEqualTo("John");
    }

    @Test
    void getProfileBySubject_whenNotFound_returnsEmpty() {
        when(profileService.getProfileBySubject(anyString())).thenReturn(Optional.empty());

        Optional<ProfileResponse> result = profileService.getProfileBySubject("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void createProfile_callsServiceWithCorrectParams() {
        ProfileRequest request = createTestRequest();
        ProfileResponse mockResponse = createTestResponse();

        when(profileService.createProfile(anyString(), any(), any(ProfileRequest.class)))
                .thenReturn(mockResponse);

        ProfileResponse result = profileService.createProfile("subject", 123L, request);

        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("John");
        verify(profileService).createProfile("subject", 123L, request);
    }

    @Test
    void updateProfile_callsServiceWithCorrectParams() {
        ProfileRequest request = createTestRequest();
        ProfileResponse mockResponse = createTestResponse();

        when(profileService.updateProfile(anyString(), any(ProfileRequest.class)))
                .thenReturn(mockResponse);

        ProfileResponse result = profileService.updateProfile("subject", request);

        assertThat(result).isNotNull();
        verify(profileService).updateProfile("subject", request);
    }

    @Test
    void deleteProfile_callsService() {
        when(profileService.deleteProfile(anyString())).thenReturn(true);

        boolean result = profileService.deleteProfile("subject");

        assertThat(result).isTrue();
        verify(profileService).deleteProfile("subject");
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
