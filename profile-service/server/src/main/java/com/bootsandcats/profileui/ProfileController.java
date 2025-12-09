package com.bootsandcats.profileui;

import java.util.Map;
import java.util.Optional;

import com.bootsandcats.profileui.dto.ProfileRequest;
import com.bootsandcats.profileui.dto.ProfileResponse;
import com.bootsandcats.profileui.security.AuthenticationHelper;
import com.bootsandcats.profileui.service.ProfileService;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;

/**
 * Controller for current user profile operations.
 *
 * <p>Provides endpoints for users to view and manage their own profile. Users must have the
 * profile:read scope to view and profile:write scope to modify their profile.
 */
@Controller("/api")
@Validated
@Secured(SecurityRule.IS_AUTHENTICATED)
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /** Returns the authenticated user's OAuth2 token attributes. */
    @Get(value = "/me", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> me(Authentication authentication) {
        if (authentication == null) {
            return HttpResponse.unauthorized();
        }

        return HttpResponse.ok(
                Map.of(
                        "name", authentication.getName(),
                        "attributes", authentication.getAttributes(),
                        "scopes", AuthenticationHelper.getScopes(authentication),
                        "hasProfile", profileService.profileExists(authentication.getName())));
    }

    /**
     * Get the current user's profile.
     *
     * @param authentication the authenticated user
     * @return the profile or 404 if not found
     */
    @Get(value = "/profile", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getProfile(Authentication authentication) {
        if (authentication == null) {
            return HttpResponse.unauthorized();
        }

        String subject = AuthenticationHelper.getSubject(authentication);
        Optional<ProfileResponse> profile = profileService.getProfileBySubject(subject);

        if (profile.isPresent()) {
            return HttpResponse.ok(profile.get());
        } else {
            return HttpResponse.notFound(
                    Map.of(
                            "exists",
                            false,
                            "message",
                            "No profile found. Create one to get started."));
        }
    }

    /**
     * Create a new profile for the current user.
     *
     * @param authentication the authenticated user
     * @param request the profile data
     * @return the created profile
     */
    @Post(value = "/profile", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> createProfile(
            Authentication authentication, @Body @Valid ProfileRequest request) {
        if (authentication == null) {
            return HttpResponse.unauthorized();
        }

        String subject = AuthenticationHelper.getSubject(authentication);
        Long userId = AuthenticationHelper.getUserId(authentication).orElse(null);

        // Check if profile already exists
        if (profileService.profileExists(subject)) {
            return HttpResponse.badRequest(
                    Map.of("error", "conflict", "message", "Profile already exists"));
        }

        // Pre-populate email from token if not provided
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            AuthenticationHelper.getEmail(authentication).ifPresent(request::setEmail);
        }

        ProfileResponse profile = profileService.createProfile(subject, userId, request);
        return HttpResponse.created(profile);
    }

    /**
     * Update the current user's profile.
     *
     * @param authentication the authenticated user
     * @param request the profile data
     * @return the updated profile
     */
    @Put(value = "/profile", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> updateProfile(
            Authentication authentication, @Body @Valid ProfileRequest request) {
        if (authentication == null) {
            return HttpResponse.unauthorized();
        }

        String subject = AuthenticationHelper.getSubject(authentication);

        // Check if profile exists
        if (!profileService.profileExists(subject)) {
            return HttpResponse.notFound(
                    Map.of(
                            "error",
                            "not_found",
                            "message",
                            "Profile not found. Create one first."));
        }

        ProfileResponse profile = profileService.updateProfile(subject, request);
        return HttpResponse.ok(profile);
    }

    /**
     * Delete the current user's profile.
     *
     * @param authentication the authenticated user
     * @return success or not found
     */
    @Delete(value = "/profile", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> deleteProfile(Authentication authentication) {
        if (authentication == null) {
            return HttpResponse.unauthorized();
        }

        String subject = AuthenticationHelper.getSubject(authentication);

        if (profileService.deleteProfile(subject)) {
            return HttpResponse.ok(Map.of("deleted", true));
        } else {
            return HttpResponse.notFound(
                    Map.of("error", "not_found", "message", "Profile not found"));
        }
    }
}
