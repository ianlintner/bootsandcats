package com.bootsandcats.oauth2.service.admin;

/** Thrown when an admin-managed resource cannot be found. */
public class AdminResourceNotFoundException extends RuntimeException {

    public AdminResourceNotFoundException(String message) {
        super(message);
    }
}
