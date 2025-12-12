package com.bootsandcats.oauth2.dto.admin;

import com.bootsandcats.oauth2.model.DenyMatchField;
import com.bootsandcats.oauth2.model.DenyMatchType;

/** Summary view of a deny rule for admin management. */
public record AdminDenyRuleSummary(
        Long id,
        boolean enabled,
        String provider,
        DenyMatchField matchField,
        DenyMatchType matchType,
        String pattern,
        String reason) {}
