package com.bootsandcats.oauth2.model;

/**
 * Enumeration representing the result/outcome of a security audit event.
 *
 * <p>Used to indicate whether an audited action succeeded, failed, or was denied.
 */
public enum AuditEventResult {

    /** The action completed successfully. */
    SUCCESS("Operation completed successfully"),

    /** The action failed due to an error or invalid input. */
    FAILURE("Operation failed"),

    /** The action was denied due to insufficient permissions or policy violation. */
    DENIED("Operation denied"),

    /** The action is pending (e.g., awaiting user approval). */
    PENDING("Operation pending");

    private final String description;

    AuditEventResult(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
