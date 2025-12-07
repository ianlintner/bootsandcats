package com.bootsandcats.profileui;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.Map;

/** Simple landing endpoint for profile UI service. */
@Controller("/")
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
