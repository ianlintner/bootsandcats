package com.bootsandcats.profileui;

import java.util.Map;
import java.util.Optional;

import com.bootsandcats.profileui.dto.ProfileRequest;
import com.bootsandcats.profileui.dto.ProfileResponse;
import com.bootsandcats.profileui.service.ProfileService;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;

/**
 * Controller for current user profile operations.
 *
 * <p>Provides endpoints for users to view and manage their own profile. Envoy OAuth2 filter handles
 * authentication and extracts JWT claims to headers.
 */
@Controller("/api")
@Validated
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /** Returns a simple response indicating the service is authenticated. */
    @Get(value = "/me", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> me() {
        // Envoy handles authentication - if request reaches here, user is authenticated
        return HttpResponse.ok(
                Map.of(
                        "authenticated",
                        true,
                        "message",
                        "User authenticated via Envoy OAuth2 filter"));
    }

    /**
     * Get the current user's profile.
     *
     * @param subject the user subject from JWT (x-jwt-sub header)
     * @return the profile or 404 if not found
     */
    @Get(value = "/profile", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getProfile(@Header(value = "x-jwt-sub", defaultValue = "") String subject) {
        if (subject == null || subject.isBlank()) {
            return HttpResponse.unauthorized();
        }

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
     * @param subject the user subject from JWT (x-jwt-sub header)
     * @param email the user email from JWT (x-jwt-email header)
     * @param request the profile data
     * @return the created profile
     */
    @Post(value = "/profile", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> createProfile(
            @Header(value = "x-jwt-sub", defaultValue = "") String subject,
            @Header(value = "x-jwt-email", defaultValue = "") String email,
            @Body @Valid ProfileRequest request) {
        if (subject == null || subject.isBlank()) {
            return HttpResponse.unauthorized();
        }

        // Check if profile already exists
        if (profileService.profileExists(subject)) {
            return HttpResponse.badRequest(
                    Map.of("error", "conflict", "message", "Profile already exists"));
        }

        // Pre-populate email from JWT if not provided in request
        if ((request.getEmail() == null || request.getEmail().isBlank())
                && email != null
                && !email.isBlank()) {
            request.setEmail(email);
        }

        ProfileResponse profile = profileService.createProfile(subject, null, request);
        return HttpResponse.created(profile);
    }

    /**
     * Update the current user's profile.
     *
     * @param subject the user subject from JWT (x-jwt-sub header)
     * @param request the profile data
     * @return the updated profile
     */
    @Put(value = "/profile", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> updateProfile(
            @Header(value = "x-jwt-sub", defaultValue = "") String subject,
            @Body @Valid ProfileRequest request) {
        if (subject == null || subject.isBlank()) {
            return HttpResponse.unauthorized();
        }

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
     * @param subject the user subject from JWT (x-jwt-sub header)
     * @return success or not found
     */
    @Delete(value = "/profile", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> deleteProfile(@Header(value = "x-jwt-sub", defaultValue = "") String subject) {
        if (subject == null || subject.isBlank()) {
            return HttpResponse.unauthorized();
        }

        if (profileService.deleteProfile(subject)) {
            return HttpResponse.ok(Map.of("deleted", true));
        } else {
            return HttpResponse.notFound(
                    Map.of("error", "not_found", "message", "Profile not found"));
        }
    }
}
