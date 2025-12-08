package com.bootsandcats.profileui.repository;

import java.util.List;
import java.util.Optional;

import com.bootsandcats.profileui.model.UserProfile;

/** Repository interface for user profile data access operations. */
public interface ProfileRepository {

    /**
     * Find a profile by the OAuth2 subject identifier.
     *
     * @param oauthSubject the OAuth2 subject (sub claim)
     * @return the profile if found
     */
    Optional<UserProfile> findByOauthSubject(String oauthSubject);

    /**
     * Find a profile by the OAuth2 user ID.
     *
     * @param oauthUserId the OAuth2 user ID
     * @return the profile if found
     */
    Optional<UserProfile> findByOauthUserId(Long oauthUserId);

    /**
     * Find a profile by its MongoDB ID.
     *
     * @param id the MongoDB document ID as hex string
     * @return the profile if found
     */
    Optional<UserProfile> findById(String id);

    /**
     * Find all profiles with pagination.
     *
     * @param page the page number (0-based)
     * @param pageSize the number of profiles per page
     * @return list of profiles
     */
    List<UserProfile> findAll(int page, int pageSize);

    /**
     * Count the total number of profiles.
     *
     * @return the total count
     */
    long count();

    /**
     * Search profiles by name or email.
     *
     * @param query the search query
     * @param page the page number (0-based)
     * @param pageSize the number of profiles per page
     * @return list of matching profiles
     */
    List<UserProfile> search(String query, int page, int pageSize);

    /**
     * Count profiles matching a search query.
     *
     * @param query the search query
     * @return the count of matching profiles
     */
    long countSearch(String query);

    /**
     * Save a new profile or update an existing one.
     *
     * @param profile the profile to save
     * @return the saved profile
     */
    UserProfile save(UserProfile profile);

    /**
     * Delete a profile by its MongoDB ID.
     *
     * @param id the MongoDB document ID as hex string
     * @return true if deleted, false if not found
     */
    boolean deleteById(String id);

    /**
     * Delete a profile by the OAuth2 subject identifier.
     *
     * @param oauthSubject the OAuth2 subject
     * @return true if deleted, false if not found
     */
    boolean deleteByOauthSubject(String oauthSubject);

    /**
     * Check if a profile exists for the given OAuth2 subject.
     *
     * @param oauthSubject the OAuth2 subject
     * @return true if exists
     */
    boolean existsByOauthSubject(String oauthSubject);
}
