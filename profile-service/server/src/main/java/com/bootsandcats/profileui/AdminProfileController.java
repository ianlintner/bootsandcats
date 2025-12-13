package com.bootsandcats.profileui;

import java.util.Map;
import java.util.Optional;

import com.bootsandcats.profileui.dto.ProfileListResponse;
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
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.validation.Validated;
import jakarta.validation.Valid;

/**
 * Admin controller for managing all user profiles.
 *
 * <p>Provides endpoints for administrators to list, view, edit, and delete any user's profile.
 * Envoy OAuth2 filter handles authentication.
 */
@Controller("/api/admin/profiles")
@Validated
public class AdminProfileController {

    private final ProfileService profileService;

    public AdminProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * List all profiles with pagination.
     *
     * @param scopes the JWT scopes from x-jwt-scope header
     * @param page page number (0-based)
     * @param pageSize number of profiles per page
     * @param search optional search query
     * @return paginated list of profiles
     */
    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> listProfiles(
            @Header(value = "x-jwt-scope", defaultValue = "") String scopes,
            @QueryValue(defaultValue = "0") int page,
            @QueryValue(defaultValue = "20") int pageSize,
            @QueryValue Optional<String> search) {
        // Check for admin scope
        if (!scopes.contains("profile:admin")) {
            return HttpResponse.status(io.micronaut.http.HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "message", "Admin access required"));
        }

        // Validate pagination params
        if (page < 0) {
            page = 0;
        }
        if (pageSize < 1 || pageSize > 100) {
            pageSize = 20;
        }

        ProfileListResponse response;
        if (search.isPresent() && !search.get().isBlank()) {
            response = profileService.searchProfiles(search.get(), page, pageSize);
        } else {
            response = profileService.listProfiles(page, pageSize);
        }

        return HttpResponse.ok(response);
    }

    /**
     * Get a specific profile by ID.
     *
     * @param scopes the JWT scopes from x-jwt-scope header
     * @param id the profile MongoDB ID
     * @return the profile or 404
     */
    @Get(value = "/{id}", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getProfile(
            @Header(value = "x-jwt-scope", defaultValue = "") String scopes,
            @PathVariable String id) {
        // Check for admin scope
        if (!scopes.contains("profile:admin")) {
            return HttpResponse.status(io.micronaut.http.HttpStatus.FORBIDDEN)
                    .body(
                            Map.of(
                                    "error",
                                    "forbidden",
                                    "message",
                                    "Admin access required to view other profiles"));
        }

        Optional<ProfileResponse> profile = profileService.getProfileById(id);

        if (profile.isPresent()) {
            return HttpResponse.ok(profile.get());
        } else {
            return HttpResponse.notFound(
                    Map.of("error", "not_found", "message", "Profile not found"));
        }
    }

    /**
     * Update a profile by ID.
     *
     * @param scopes the JWT scopes from x-jwt-scope header
     * @param id the profile MongoDB ID
     * @param request the updated profile data
     * @return the updated profile
     */
    @Put(value = "/{id}", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> updateProfile(
            @Header(value = "x-jwt-scope", defaultValue = "") String scopes,
            @PathVariable String id,
            @Body @Valid ProfileRequest request) {
        // Check for admin scope
        if (!scopes.contains("profile:admin")) {
            return HttpResponse.status(io.micronaut.http.HttpStatus.FORBIDDEN)
                    .body(
                            Map.of(
                                    "error",
                                    "forbidden",
                                    "message",
                                    "Admin access required to edit other profiles"));
        }

        try {
            ProfileResponse profile = profileService.updateProfileById(id, request);
            return HttpResponse.ok(profile);
        } catch (com.bootsandcats.profileui.exception.ProfileNotFoundException e) {
            return HttpResponse.notFound(Map.of("error", "not_found", "message", e.getMessage()));
        }
    }

    /**
     * Delete a profile by ID.
     *
     * @param scopes the JWT scopes from x-jwt-scope header
     * @param id the profile MongoDB ID
     * @return success or not found
     */
    @Delete(value = "/{id}", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> deleteProfile(
            @Header(value = "x-jwt-scope", defaultValue = "") String scopes,
            @PathVariable String id) {
        // Check for admin scope
        if (!scopes.contains("profile:admin")) {
            return HttpResponse.status(io.micronaut.http.HttpStatus.FORBIDDEN)
                    .body(
                            Map.of(
                                    "error",
                                    "forbidden",
                                    "message",
                                    "Admin access required to delete profiles"));
        }

        if (profileService.deleteProfileById(id)) {
            return HttpResponse.ok(Map.of("deleted", true, "id", id));
        } else {
            return HttpResponse.notFound(
                    Map.of("error", "not_found", "message", "Profile not found"));
        }
    }
}
