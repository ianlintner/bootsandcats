package com.bootsandcats.oauth2.k8s;

import lombok.Data;

@Data
public class OAuth2DenyRuleSpec {
    private Boolean enabled;

    /** Nullable: when null, rule applies to all providers. "*" indicates wildcard. */
    private String provider;

    /** One of EMAIL, USERNAME, PROVIDER_ID. */
    private String matchField;

    /** One of EXACT, REGEX. */
    private String matchType;

    private String pattern;

    /** Optional normalized value for exact matches. */
    private String normalizedValue;

    private String reason;

    private String createdBy;
    /** ISO-8601 instant string. */
    private String createdAt;
    /** ISO-8601 instant string. */
    private String updatedAt;
}
