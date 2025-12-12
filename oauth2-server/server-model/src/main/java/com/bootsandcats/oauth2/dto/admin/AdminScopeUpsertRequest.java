package com.bootsandcats.oauth2.dto.admin;

import jakarta.validation.constraints.NotBlank;

/** Create/update request for the scope catalog. */
public record AdminScopeUpsertRequest(
        @NotBlank String scope, String description, boolean enabled) {}
