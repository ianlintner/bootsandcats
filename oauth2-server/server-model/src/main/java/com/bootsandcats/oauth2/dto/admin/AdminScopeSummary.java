package com.bootsandcats.oauth2.dto.admin;

/** Summary view of a scope in the admin-managed scope catalog. */
public record AdminScopeSummary(
        String scope, String description, boolean enabled, boolean system) {}
