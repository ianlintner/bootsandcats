package com.bootsandcats.oauth2.dto.admin;

import com.bootsandcats.oauth2.model.DenyMatchField;
import com.bootsandcats.oauth2.model.DenyMatchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Create/update request for deny rules. */
public record AdminDenyRuleUpsertRequest(
        boolean enabled,
        /** Nullable/blank means global; '*' is also treated as global by the query layer. */
        String provider,
        @NotNull DenyMatchField matchField,
        @NotNull DenyMatchType matchType,
        @NotBlank String pattern,
        String reason) {}
