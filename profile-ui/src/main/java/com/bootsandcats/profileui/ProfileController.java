package com.bootsandcats.profileui;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.authentication.Authentication;
import java.util.Map;

/** Returns the authenticated user's profile attributes. */
@Controller("/api/me")
class ProfileController {

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> me(Authentication authentication) {
        if (authentication == null) {
            return HttpResponse.unauthorized();
        }

        return HttpResponse.ok(
                Map.of(
                        "name", authentication.getName(),
                        "attributes", authentication.getAttributes()));
    }
}
