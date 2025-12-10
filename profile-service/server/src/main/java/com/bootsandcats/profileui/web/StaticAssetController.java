package com.bootsandcats.profileui.web;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;

import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.types.files.StreamedFile;

/**
 * Explicit controllers for static assets that must be reachable in locked-down environments (e.g.,
 * when the static resource resolver is bypassed or security filters run first).
 */
@Controller
public class StaticAssetController {

    private static final MediaType JAVASCRIPT = MediaType.of("application/javascript");
    private static final MediaType ICON = new MediaType("image/x-icon");

    private final ResourceResolver resourceResolver;

    public StaticAssetController(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    @Get(value = "/profile-app.js", produces = "application/javascript")
    public HttpResponse<StreamedFile> profileAppJs() {
        return resolveAsStream("classpath:public/profile-app.js", JAVASCRIPT);
    }

    @Get(value = "/favicon.ico", produces = "image/x-icon")
    public HttpResponse<StreamedFile> favicon() {
        return resolveAsStream("classpath:public/favicon.ico", ICON);
    }

    private HttpResponse<StreamedFile> resolveAsStream(String location, MediaType mediaType) {
        Optional<URL> resource = resourceResolver.getResource(location);
        if (resource.isEmpty()) {
            return HttpResponse.notFound();
        }
        try {
            StreamedFile file = new StreamedFile(resource.get().openStream(), mediaType);
            return HttpResponse.ok(file);
        } catch (IOException e) {
            return HttpResponse.serverError();
        }
    }
}
