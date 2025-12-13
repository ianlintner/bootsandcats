package com.bootsandcats.profileui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;

/**
 * Root controller for the profile service.
 *
 * <p>Serves the static index.html for the root path. Envoy OAuth2 filter handles authentication.
 */
@Controller
public class RootController {

    private static final String INDEX_HTML;

    static {
        try (InputStream is =
                RootController.class.getClassLoader().getResourceAsStream("public/index.html")) {
            if (is != null) {
                INDEX_HTML = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } else {
                INDEX_HTML = null;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load index.html", e);
        }
    }

    /**
     * Serve the landing page at the root path.
     *
     * @return the index.html content
     */
    @Get("/")
    @Produces(MediaType.TEXT_HTML)
    HttpResponse<String> index() {
        if (INDEX_HTML != null) {
            return HttpResponse.ok(INDEX_HTML);
        }
        return HttpResponse.notFound();
    }
}
