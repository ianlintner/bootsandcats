package com.bootsandcats.profileui.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.bootsandcats.profileui.dto.ProfileListResponse;
import com.bootsandcats.profileui.dto.ProfileRequest;
import com.bootsandcats.profileui.dto.ProfileResponse;
import com.bootsandcats.profileui.exception.ProfileNotFoundException;
import com.bootsandcats.profileui.model.UserProfile;
import com.bootsandcats.profileui.repository.ProfileRepository;

import jakarta.inject.Singleton;

/** Service layer for profile management operations. */
@Singleton
public class ProfileService {

    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /**
     * Get a profile by OAuth2 subject identifier.
     *
     * @param oauthSubject the OAuth2 subject (sub claim from JWT)
     * @return the profile if found
     */
    public Optional<ProfileResponse> getProfileBySubject(String oauthSubject) {
        return profileRepository.findByOauthSubject(oauthSubject).map(ProfileResponse::fromEntity);
    }

    /**
     * Get a profile by MongoDB ID.
     *
     * @param id the MongoDB document ID
     * @return the profile if found
     */
    public Optional<ProfileResponse> getProfileById(String id) {
        return profileRepository.findById(id).map(ProfileResponse::fromEntity);
    }

    /**
     * Check if a profile exists for the given OAuth2 subject.
     *
     * @param oauthSubject the OAuth2 subject
     * @return true if exists
     */
    public boolean profileExists(String oauthSubject) {
        return profileRepository.existsByOauthSubject(oauthSubject);
    }

    /**
     * Create a new profile for the current user.
     *
     * @param oauthSubject the OAuth2 subject
     * @param oauthUserId the OAuth2 user ID (may be null)
     * @param request the profile data
     * @return the created profile
     */
    public ProfileResponse createProfile(
            String oauthSubject, Long oauthUserId, ProfileRequest request) {
        if (profileRepository.existsByOauthSubject(oauthSubject)) {
            throw new IllegalStateException("Profile already exists for subject: " + oauthSubject);
        }

        UserProfile profile = new UserProfile();
        profile.setOauthSubject(oauthSubject);
        profile.setOauthUserId(oauthUserId);
        updateProfileFromRequest(profile, request);
        profile.setCreatedAt(Instant.now());
        profile.setUpdatedAt(Instant.now());

        UserProfile saved = profileRepository.save(profile);
        return ProfileResponse.fromEntity(saved);
    }

    /**
     * Update an existing profile for the current user.
     *
     * @param oauthSubject the OAuth2 subject
     * @param request the profile data
     * @return the updated profile
     */
    public ProfileResponse updateProfile(String oauthSubject, ProfileRequest request) {
        UserProfile profile =
                profileRepository
                        .findByOauthSubject(oauthSubject)
                        .orElseThrow(
                                () ->
                                        new ProfileNotFoundException(
                                                "Profile not found for subject: " + oauthSubject));

        updateProfileFromRequest(profile, request);
        profile.setUpdatedAt(Instant.now());

        UserProfile saved = profileRepository.save(profile);
        return ProfileResponse.fromEntity(saved);
    }

    /**
     * Update a profile by ID (for admin use).
     *
     * @param id the profile MongoDB ID
     * @param request the profile data
     * @return the updated profile
     */
    public ProfileResponse updateProfileById(String id, ProfileRequest request) {
        UserProfile profile =
                profileRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ProfileNotFoundException("Profile not found: " + id));

        updateProfileFromRequest(profile, request);
        profile.setUpdatedAt(Instant.now());

        UserProfile saved = profileRepository.save(profile);
        return ProfileResponse.fromEntity(saved);
    }

    /**
     * Delete a profile by OAuth2 subject.
     *
     * @param oauthSubject the OAuth2 subject
     * @return true if deleted
     */
    public boolean deleteProfile(String oauthSubject) {
        return profileRepository.deleteByOauthSubject(oauthSubject);
    }

    /**
     * Delete a profile by ID (for admin use).
     *
     * @param id the profile MongoDB ID
     * @return true if deleted
     */
    public boolean deleteProfileById(String id) {
        return profileRepository.deleteById(id);
    }

    /**
     * List all profiles with pagination.
     *
     * @param page the page number (0-based)
     * @param pageSize the page size
     * @return paginated list of profiles
     */
    public ProfileListResponse listProfiles(int page, int pageSize) {
        List<ProfileResponse> profiles =
                profileRepository.findAll(page, pageSize).stream()
                        .map(ProfileResponse::fromEntity)
                        .collect(Collectors.toList());

        long totalCount = profileRepository.count();

        return new ProfileListResponse(profiles, totalCount, page, pageSize);
    }

    /**
     * Search profiles by name or email.
     *
     * @param query the search query
     * @param page the page number (0-based)
     * @param pageSize the page size
     * @return paginated list of matching profiles
     */
    public ProfileListResponse searchProfiles(String query, int page, int pageSize) {
        List<ProfileResponse> profiles =
                profileRepository.search(query, page, pageSize).stream()
                        .map(ProfileResponse::fromEntity)
                        .collect(Collectors.toList());

        long totalCount = profileRepository.countSearch(query);

        return new ProfileListResponse(profiles, totalCount, page, pageSize);
    }

    private void updateProfileFromRequest(UserProfile profile, ProfileRequest request) {
        if (request.getFirstName() != null) {
            profile.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            profile.setLastName(request.getLastName());
        }
        if (request.getPreferredName() != null) {
            profile.setPreferredName(request.getPreferredName());
        }
        if (request.getEmail() != null) {
            profile.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null) {
            profile.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getAddress() != null) {
            profile.setAddress(request.getAddress());
        }
        if (request.getSocialMedia() != null) {
            profile.setSocialMedia(request.getSocialMedia());
        }
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
        }
        if (request.getPictureUrl() != null) {
            profile.setPictureUrl(request.getPictureUrl());
        }
    }
}
