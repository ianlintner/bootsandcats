package com.bootsandcats.profilelite;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import java.util.Map;

/** Simple landing endpoint for profile UI lite. */
@Controller("/")
class HomeController {

    @Get(produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map<String, Object>> index() {
        return HttpResponse.ok(
                Map.of(
                        "service", "profile-ui-lite",
                        "status", "running",
                        "message", "Profile UI Lite is ready"));
    }
}
