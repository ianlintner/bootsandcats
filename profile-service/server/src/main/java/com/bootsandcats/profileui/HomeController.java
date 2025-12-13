package com.bootsandcats.profileui;

import java.util.Map;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

/** Health-style JSON endpoint for the profile UI service. */
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
