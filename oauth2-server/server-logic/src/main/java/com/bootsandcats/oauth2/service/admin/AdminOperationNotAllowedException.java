package com.bootsandcats.oauth2.service.admin;

/** Thrown when an admin attempts to change a protected/system-managed resource. */
public class AdminOperationNotAllowedException extends RuntimeException {

    public AdminOperationNotAllowedException(String message) {
        super(message);
    }
}
