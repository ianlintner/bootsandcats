package com.bootsandcats.profileui.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.bootsandcats.profileui.model.Address;
import com.bootsandcats.profileui.model.SocialMedia;
import com.bootsandcats.profileui.model.UserProfile;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.*;

/**
 * Integration tests for MongoProfileRepository.
 *
 * <p>Note: These tests require a MongoDB instance to be running.
 * For local development, use Docker or the embedded MongoDB for testing.
 * Tests are disabled by default when MongoDB is not available.
 */
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled("Requires MongoDB to be running - enable for integration testing")
class MongoProfileRepositoryIntegrationTest {

    @Inject
    ProfileRepository profileRepository;

    @BeforeEach
    void cleanUp() {
        // Clean up any existing test data
        List<UserProfile> profiles = profileRepository.findAll(0, 1000);
        for (UserProfile profile : profiles) {
            if (profile.getId() != null) {
                profileRepository.deleteById(profile.getId().toHexString());
            }
        }
    }

    @Test
    void save_createsNewProfile() {
        UserProfile profile = createTestProfile("test-subject-1");

        UserProfile saved = profileRepository.save(profile);

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFirstName()).isEqualTo("John");
        assertThat(saved.getLastName()).isEqualTo("Doe");
    }

    @Test
    void findByOauthSubject_whenExists_returnsProfile() {
        UserProfile profile = createTestProfile("unique-subject-1");
        profileRepository.save(profile);

        Optional<UserProfile> found = profileRepository.findByOauthSubject("unique-subject-1");

        assertThat(found).isPresent();
        assertThat(found.get().getFirstName()).isEqualTo("John");
        assertThat(found.get().getOauthSubject()).isEqualTo("unique-subject-1");
    }

    @Test
    void findByOauthSubject_whenNotExists_returnsEmpty() {
        Optional<UserProfile> found = profileRepository.findByOauthSubject("nonexistent-subject");

        assertThat(found).isEmpty();
    }

    @Test
    void findById_whenExists_returnsProfile() {
        UserProfile profile = createTestProfile("test-subject-2");
        UserProfile saved = profileRepository.save(profile);

        Optional<UserProfile> found = profileRepository.findById(saved.getId().toHexString());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findById_whenNotExists_returnsEmpty() {
        Optional<UserProfile> found =
                profileRepository.findById("000000000000000000000000");

        assertThat(found).isEmpty();
    }

    @Test
    void findAll_returnsPagedResults() {
        for (int i = 0; i < 15; i++) {
            profileRepository.save(createTestProfile("subject-" + i));
        }

        List<UserProfile> firstPage = profileRepository.findAll(0, 10);
        List<UserProfile> secondPage = profileRepository.findAll(1, 10);

        assertThat(firstPage).hasSize(10);
        assertThat(secondPage).hasSize(5);
    }

    @Test
    void count_returnsCorrectCount() {
        profileRepository.save(createTestProfile("count-subject-1"));
        profileRepository.save(createTestProfile("count-subject-2"));
        profileRepository.save(createTestProfile("count-subject-3"));

        long count = profileRepository.count();

        assertThat(count).isEqualTo(3);
    }

    @Test
    void search_findsMatchingProfiles() {
        UserProfile profile1 = createTestProfile("search-subject-1");
        profile1.setFirstName("Alice");
        profile1.setLastName("Johnson");
        profileRepository.save(profile1);

        UserProfile profile2 = createTestProfile("search-subject-2");
        profile2.setFirstName("Bob");
        profile2.setLastName("Smith");
        profileRepository.save(profile2);

        List<UserProfile> results = profileRepository.search("alice", 0, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFirstName()).isEqualTo("Alice");
    }

    @Test
    void save_updatesExistingProfile() {
        UserProfile profile = createTestProfile("update-subject");
        UserProfile saved = profileRepository.save(profile);

        saved.setFirstName("Updated");
        saved.setLastName("Name");
        UserProfile updated = profileRepository.save(saved);

        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(updated.getFirstName()).isEqualTo("Updated");
        assertThat(updated.getLastName()).isEqualTo("Name");
    }

    @Test
    void deleteById_removesProfile() {
        UserProfile profile = createTestProfile("delete-subject");
        UserProfile saved = profileRepository.save(profile);
        String id = saved.getId().toHexString();

        boolean deleted = profileRepository.deleteById(id);

        assertThat(deleted).isTrue();
        Optional<UserProfile> found = profileRepository.findById(id);
        assertThat(found).isEmpty();
    }

    @Test
    void deleteByOauthSubject_removesProfile() {
        UserProfile profile = createTestProfile("delete-by-subject");
        profileRepository.save(profile);

        boolean deleted = profileRepository.deleteByOauthSubject("delete-by-subject");

        assertThat(deleted).isTrue();
        Optional<UserProfile> found = profileRepository.findByOauthSubject("delete-by-subject");
        assertThat(found).isEmpty();
    }

    @Test
    void existsByOauthSubject_returnsTrueWhenExists() {
        UserProfile profile = createTestProfile("exists-subject");
        profileRepository.save(profile);

        boolean exists = profileRepository.existsByOauthSubject("exists-subject");

        assertThat(exists).isTrue();
    }

    @Test
    void existsByOauthSubject_returnsFalseWhenNotExists() {
        boolean exists = profileRepository.existsByOauthSubject("nonexistent-subject");

        assertThat(exists).isFalse();
    }

    @Test
    void save_withAddressAndSocialMedia_persistsNestedObjects() {
        UserProfile profile = createTestProfile("nested-subject");

        Address address = new Address();
        address.setStreet1("123 Test Street");
        address.setStreet2("Apt 4B");
        address.setCity("Test City");
        address.setState("TS");
        address.setPostalCode("12345");
        address.setCountry("USA");
        profile.setAddress(address);

        SocialMedia social = new SocialMedia();
        social.setTwitter("@testuser");
        social.setGithub("testuser");
        social.setLinkedin("https://linkedin.com/in/testuser");
        social.setWebsite("https://testuser.com");
        profile.setSocialMedia(social);

        UserProfile saved = profileRepository.save(profile);

        Optional<UserProfile> found = profileRepository.findById(saved.getId().toHexString());

        assertThat(found).isPresent();
        assertThat(found.get().getAddress()).isNotNull();
        assertThat(found.get().getAddress().getStreet1()).isEqualTo("123 Test Street");
        assertThat(found.get().getAddress().getStreet2()).isEqualTo("Apt 4B");
        assertThat(found.get().getAddress().getCity()).isEqualTo("Test City");

        assertThat(found.get().getSocialMedia()).isNotNull();
        assertThat(found.get().getSocialMedia().getTwitter()).isEqualTo("@testuser");
        assertThat(found.get().getSocialMedia().getGithub()).isEqualTo("testuser");
    }

    private UserProfile createTestProfile(String subject) {
        UserProfile profile = new UserProfile();
        profile.setOauthSubject(subject);
        profile.setOauthUserId(123L);
        profile.setFirstName("John");
        profile.setLastName("Doe");
        profile.setPreferredName("Johnny");
        profile.setEmail("john.doe@example.com");
        profile.setPhoneNumber("555-0100");
        profile.setBio("Test bio for " + subject);
        profile.setCreatedAt(Instant.now());
        profile.setUpdatedAt(Instant.now());
        return profile;
    }
}
