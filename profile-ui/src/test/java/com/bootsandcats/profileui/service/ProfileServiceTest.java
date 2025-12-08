package com.bootsandcats.profileui.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bootsandcats.profileui.dto.ProfileListResponse;
import com.bootsandcats.profileui.dto.ProfileRequest;
import com.bootsandcats.profileui.dto.ProfileResponse;
import com.bootsandcats.profileui.exception.ProfileNotFoundException;
import com.bootsandcats.profileui.model.Address;
import com.bootsandcats.profileui.model.SocialMedia;
import com.bootsandcats.profileui.model.UserProfile;
import com.bootsandcats.profileui.repository.ProfileRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for ProfileService.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(profileRepository);
    }

    @Test
    void getProfileBySubject_whenProfileExists_returnsProfile() {
        String subject = "user123";
        UserProfile profile = createTestProfile(subject);
        when(profileRepository.findByOauthSubject(subject)).thenReturn(Optional.of(profile));

        Optional<ProfileResponse> response = profileService.getProfileBySubject(subject);

        assertThat(response).isPresent();
        assertThat(response.get().getFirstName()).isEqualTo("John");
        assertThat(response.get().getLastName()).isEqualTo("Doe");
        assertThat(response.get().getEmail()).isEqualTo("john.doe@example.com");
        verify(profileRepository).findByOauthSubject(subject);
    }

    @Test
    void getProfileBySubject_whenProfileNotExists_returnsEmpty() {
        String subject = "nonexistent";
        when(profileRepository.findByOauthSubject(subject)).thenReturn(Optional.empty());

        Optional<ProfileResponse> response = profileService.getProfileBySubject(subject);

        assertThat(response).isEmpty();
    }

    @Test
    void getProfileById_whenProfileExists_returnsProfile() {
        String id = new ObjectId().toHexString();
        UserProfile profile = createTestProfile("user123");
        profile.setId(new ObjectId(id));
        when(profileRepository.findById(id)).thenReturn(Optional.of(profile));

        Optional<ProfileResponse> response = profileService.getProfileById(id);

        assertThat(response).isPresent();
        assertThat(response.get().getFirstName()).isEqualTo("John");
        verify(profileRepository).findById(id);
    }

    @Test
    void getProfileById_whenProfileNotExists_returnsEmpty() {
        String id = new ObjectId().toHexString();
        when(profileRepository.findById(id)).thenReturn(Optional.empty());

        Optional<ProfileResponse> response = profileService.getProfileById(id);

        assertThat(response).isEmpty();
    }

    @Test
    void createProfile_createsNewProfile() {
        String subject = "newuser";
        Long userId = 123L;
        ProfileRequest request = createTestRequest();
        UserProfile savedProfile = createTestProfile(subject);

        when(profileRepository.existsByOauthSubject(subject)).thenReturn(false);
        when(profileRepository.save(any(UserProfile.class))).thenReturn(savedProfile);

        ProfileResponse response = profileService.createProfile(subject, userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getFirstName()).isEqualTo("John");

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(captor.capture());

        UserProfile captured = captor.getValue();
        assertThat(captured.getOauthSubject()).isEqualTo(subject);
        assertThat(captured.getOauthUserId()).isEqualTo(userId);
        assertThat(captured.getFirstName()).isEqualTo("John");
        assertThat(captured.getLastName()).isEqualTo("Doe");
    }

    @Test
    void createProfile_whenProfileAlreadyExists_throwsException() {
        String subject = "existinguser";
        ProfileRequest request = createTestRequest();

        when(profileRepository.existsByOauthSubject(subject)).thenReturn(true);

        assertThatThrownBy(() -> profileService.createProfile(subject, null, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(subject);
    }

    @Test
    void updateProfile_whenProfileExists_updatesProfile() {
        String subject = "user123";
        UserProfile existingProfile = createTestProfile(subject);
        existingProfile.setId(new ObjectId());

        ProfileRequest updateRequest = new ProfileRequest();
        updateRequest.setFirstName("Jane");
        updateRequest.setLastName("Smith");
        updateRequest.setPreferredName("Janie");
        updateRequest.setEmail("jane@example.com");
        updateRequest.setPhoneNumber("555-1234");

        when(profileRepository.findByOauthSubject(subject)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse response = profileService.updateProfile(subject, updateRequest);

        assertThat(response).isNotNull();
        assertThat(response.getFirstName()).isEqualTo("Jane");
        assertThat(response.getLastName()).isEqualTo("Smith");

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(captor.capture());

        UserProfile captured = captor.getValue();
        assertThat(captured.getFirstName()).isEqualTo("Jane");
        assertThat(captured.getLastName()).isEqualTo("Smith");
        assertThat(captured.getPreferredName()).isEqualTo("Janie");
        assertThat(captured.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateProfile_whenProfileNotExists_throwsException() {
        String subject = "nonexistent";
        ProfileRequest request = createTestRequest();
        when(profileRepository.findByOauthSubject(subject)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.updateProfile(subject, request))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void updateProfileById_whenProfileExists_updatesProfile() {
        String id = new ObjectId().toHexString();
        UserProfile existingProfile = createTestProfile("user123");
        existingProfile.setId(new ObjectId(id));

        ProfileRequest updateRequest = new ProfileRequest();
        updateRequest.setFirstName("Updated");
        updateRequest.setLastName("Name");
        updateRequest.setEmail("updated@example.com");

        when(profileRepository.findById(id)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse response = profileService.updateProfileById(id, updateRequest);

        assertThat(response.getFirstName()).isEqualTo("Updated");
        assertThat(response.getLastName()).isEqualTo("Name");
    }

    @Test
    void deleteProfile_deletesProfile() {
        String subject = "user123";

        when(profileRepository.deleteByOauthSubject(subject)).thenReturn(true);

        boolean result = profileService.deleteProfile(subject);

        assertThat(result).isTrue();
        verify(profileRepository).deleteByOauthSubject(subject);
    }

    @Test
    void deleteProfileById_deletesProfile() {
        String id = new ObjectId().toHexString();

        when(profileRepository.deleteById(id)).thenReturn(true);

        boolean result = profileService.deleteProfileById(id);

        assertThat(result).isTrue();
        verify(profileRepository).deleteById(id);
    }

    @Test
    void listProfiles_returnsPagedProfiles() {
        List<UserProfile> profiles =
                List.of(createTestProfile("user1"), createTestProfile("user2"));

        when(profileRepository.findAll(0, 10)).thenReturn(profiles);
        when(profileRepository.count()).thenReturn(2L);

        ProfileListResponse response = profileService.listProfiles(0, 10);

        assertThat(response.getProfiles()).hasSize(2);
        assertThat(response.getTotalCount()).isEqualTo(2);
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getPageSize()).isEqualTo(10);
    }

    @Test
    void searchProfiles_returnsMatchingProfiles() {
        List<UserProfile> profiles = List.of(createTestProfile("user1"));

        when(profileRepository.search("john", 0, 10)).thenReturn(profiles);
        when(profileRepository.countSearch("john")).thenReturn(1L);

        ProfileListResponse response = profileService.searchProfiles("john", 0, 10);

        assertThat(response.getProfiles()).hasSize(1);
        assertThat(response.getTotalCount()).isEqualTo(1);
    }

    @Test
    void profileExists_whenProfileExists_returnsTrue() {
        String subject = "user123";
        when(profileRepository.existsByOauthSubject(subject)).thenReturn(true);

        assertThat(profileService.profileExists(subject)).isTrue();
    }

    @Test
    void profileExists_whenProfileNotExists_returnsFalse() {
        String subject = "nonexistent";
        when(profileRepository.existsByOauthSubject(subject)).thenReturn(false);

        assertThat(profileService.profileExists(subject)).isFalse();
    }

    private UserProfile createTestProfile(String subject) {
        UserProfile profile = new UserProfile();
        profile.setId(new ObjectId());
        profile.setOauthSubject(subject);
        profile.setOauthUserId(123L);
        profile.setFirstName("John");
        profile.setLastName("Doe");
        profile.setPreferredName("Johnny");
        profile.setEmail("john.doe@example.com");
        profile.setPhoneNumber("555-0100");
        profile.setBio("Test bio");

        Address address = new Address();
        address.setStreet1("123 Main St");
        address.setCity("Test City");
        address.setState("TS");
        address.setPostalCode("12345");
        address.setCountry("USA");
        profile.setAddress(address);

        SocialMedia social = new SocialMedia();
        social.setTwitter("@johndoe");
        social.setGithub("johndoe");
        profile.setSocialMedia(social);

        profile.setCreatedAt(Instant.now());
        profile.setUpdatedAt(Instant.now());

        return profile;
    }

    private ProfileRequest createTestRequest() {
        ProfileRequest request = new ProfileRequest();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setPreferredName("Johnny");
        request.setEmail("john.doe@example.com");
        request.setPhoneNumber("555-0100");
        request.setBio("Test bio");

        Address address = new Address();
        address.setStreet1("123 Main St");
        address.setCity("Test City");
        address.setState("TS");
        address.setPostalCode("12345");
        address.setCountry("USA");
        request.setAddress(address);

        SocialMedia social = new SocialMedia();
        social.setTwitter("@johndoe");
        social.setGithub("johndoe");
        request.setSocialMedia(social);

        return request;
    }
}
