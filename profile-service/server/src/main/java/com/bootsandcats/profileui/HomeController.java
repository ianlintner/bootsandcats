package com.bootsandcats.profileui;

import java.util.Map;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

/** Health-style JSON endpoint for the profile UI service. */
@Secured(SecurityRule.IS_ANONYMOUS)
@Controller("/api/status")
class HomeController {

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> index() {
        return HttpResponse.ok(
                Map.of(
                        "service", "profile-ui",
                        "status", "running",
                        "message", "Profile UI is ready"));
    }
}
