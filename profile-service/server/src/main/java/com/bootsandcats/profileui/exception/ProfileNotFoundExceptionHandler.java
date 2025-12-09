package com.bootsandcats.profileui.exception;

import java.util.Map;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

/** Exception handler for ProfileNotFoundException. */
@Produces
@Singleton
@Requires(classes = {ProfileNotFoundException.class, ExceptionHandler.class})
public class ProfileNotFoundExceptionHandler
        implements ExceptionHandler<ProfileNotFoundException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, ProfileNotFoundException exception) {
        return HttpResponse.notFound(
                Map.of("error", "not_found", "message", exception.getMessage()));
    }
}
