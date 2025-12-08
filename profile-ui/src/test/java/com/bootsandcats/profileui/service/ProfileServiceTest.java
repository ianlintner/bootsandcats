package com.bootsandcats.profileui.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock private ProfileRepository profileRepository;

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

        ProfileResponse response = profileService.getProfileBySubject(subject);

        assertThat(response).isNotNull();
        assertThat(response.firstName()).isEqualTo("John");
        assertThat(response.lastName()).isEqualTo("Doe");
        assertThat(response.email()).isEqualTo("john.doe@example.com");
        verify(profileRepository).findByOauthSubject(subject);
    }

    @Test
    void getProfileBySubject_whenProfileNotExists_throwsException() {
        String subject = "nonexistent";
        when(profileRepository.findByOauthSubject(subject)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfileBySubject(subject))
                .isInstanceOf(ProfileNotFoundException.class)
                .hasMessageContaining(subject);
    }

    @Test
    void getProfileById_whenProfileExists_returnsProfile() {
        String id = new ObjectId().toHexString();
        UserProfile profile = createTestProfile("user123");
        when(profileRepository.findById(id)).thenReturn(Optional.of(profile));

        ProfileResponse response = profileService.getProfileById(id);

        assertThat(response).isNotNull();
        assertThat(response.firstName()).isEqualTo("John");
        verify(profileRepository).findById(id);
    }

    @Test
    void getProfileById_whenProfileNotExists_throwsException() {
        String id = new ObjectId().toHexString();
        when(profileRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfileById(id))
                .isInstanceOf(ProfileNotFoundException.class)
                .hasMessageContaining(id);
    }

    @Test
    void createProfile_createsNewProfile() {
        String subject = "newuser";
        String userId = "userid123";
        ProfileRequest request = createTestRequest();
        UserProfile savedProfile = createTestProfile(subject);

        when(profileRepository.save(any(UserProfile.class))).thenReturn(savedProfile);

        ProfileResponse response = profileService.createProfile(subject, userId, request);

        assertThat(response).isNotNull();
        assertThat(response.firstName()).isEqualTo("John");

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profileRepository).save(captor.capture());

        UserProfile captured = captor.getValue();
        assertThat(captured.getOauthSubject()).isEqualTo(subject);
        assertThat(captured.getOauthUserId()).isEqualTo(userId);
        assertThat(captured.getFirstName()).isEqualTo("John");
        assertThat(captured.getLastName()).isEqualTo("Doe");
    }

    @Test
    void updateProfile_whenProfileExists_updatesProfile() {
        String subject = "user123";
        UserProfile existingProfile = createTestProfile(subject);
        existingProfile.setId(new ObjectId());

        ProfileRequest updateRequest =
                new ProfileRequest(
                        "Jane", "Smith", "Janie", "jane@example.com", "555-1234", null, null, null);

        when(profileRepository.findByOauthSubject(subject)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse response = profileService.updateProfile(subject, updateRequest);

        assertThat(response).isNotNull();
        assertThat(response.firstName()).isEqualTo("Jane");
        assertThat(response.lastName()).isEqualTo("Smith");

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

        ProfileRequest updateRequest =
                new ProfileRequest(
                        "Updated", "Name", null, "updated@example.com", null, null, null, null);

        when(profileRepository.findById(id)).thenReturn(Optional.of(existingProfile));
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        ProfileResponse response = profileService.updateProfileById(id, updateRequest);

        assertThat(response.firstName()).isEqualTo("Updated");
        assertThat(response.lastName()).isEqualTo("Name");
    }

    @Test
    void deleteProfile_whenProfileExists_deletesProfile() {
        String subject = "user123";
        UserProfile profile = createTestProfile(subject);
        profile.setId(new ObjectId());

        when(profileRepository.findByOauthSubject(subject)).thenReturn(Optional.of(profile));
        doNothing().when(profileRepository).delete(profile.getId().toHexString());

        profileService.deleteProfile(subject);

        verify(profileRepository).delete(profile.getId().toHexString());
    }

    @Test
    void deleteProfile_whenProfileNotExists_throwsException() {
        String subject = "nonexistent";
        when(profileRepository.findByOauthSubject(subject)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.deleteProfile(subject))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void deleteProfileById_deletesProfile() {
        String id = new ObjectId().toHexString();
        doNothing().when(profileRepository).delete(id);

        profileService.deleteProfileById(id);

        verify(profileRepository).delete(id);
    }

    @Test
    void listProfiles_returnsPagedProfiles() {
        List<UserProfile> profiles =
                List.of(createTestProfile("user1"), createTestProfile("user2"));

        when(profileRepository.findAll(0, 10)).thenReturn(profiles);
        when(profileRepository.count()).thenReturn(2L);

        var response = profileService.listProfiles(0, 10);

        assertThat(response.profiles()).hasSize(2);
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.pageSize()).isEqualTo(10);
    }

    @Test
    void searchProfiles_returnsMatchingProfiles() {
        List<UserProfile> profiles = List.of(createTestProfile("user1"));

        when(profileRepository.search("john", 0, 10)).thenReturn(profiles);
        when(profileRepository.countSearch("john")).thenReturn(1L);

        var response = profileService.searchProfiles("john", 0, 10);

        assertThat(response.profiles()).hasSize(1);
        assertThat(response.totalCount()).isEqualTo(1);
    }

    @Test
    void profileExists_whenProfileExists_returnsTrue() {
        String subject = "user123";
        when(profileRepository.findByOauthSubject(subject))
                .thenReturn(Optional.of(createTestProfile(subject)));

        assertThat(profileService.profileExists(subject)).isTrue();
    }

    @Test
    void profileExists_whenProfileNotExists_returnsFalse() {
        String subject = "nonexistent";
        when(profileRepository.findByOauthSubject(subject)).thenReturn(Optional.empty());

        assertThat(profileService.profileExists(subject)).isFalse();
    }

    private UserProfile createTestProfile(String subject) {
        UserProfile profile = new UserProfile();
        profile.setId(new ObjectId());
        profile.setOauthSubject(subject);
        profile.setOauthUserId("userid-" + subject);
        profile.setFirstName("John");
        profile.setLastName("Doe");
        profile.setPreferredName("Johnny");
        profile.setEmail("john.doe@example.com");
        profile.setPhoneNumber("555-0100");
        profile.setBio("Test bio");

        Address address = new Address();
        address.setStreet("123 Main St");
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
        return new ProfileRequest(
                "John",
                "Doe",
                "Johnny",
                "john.doe@example.com",
                "555-0100",
                "Test bio",
                new ProfileRequest.AddressRequest(
                        "123 Main St", "Test City", "TS", "12345", "USA"),
                new ProfileRequest.SocialMediaRequest("@johndoe", null, "johndoe", null));
    }
}
