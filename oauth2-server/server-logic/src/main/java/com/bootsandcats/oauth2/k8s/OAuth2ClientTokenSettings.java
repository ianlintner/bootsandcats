package com.bootsandcats.oauth2.k8s;

import lombok.Data;

@Data
public class OAuth2ClientTokenSettings {
    /** ISO-8601 duration string (e.g. PT15M). */
    private String accessTokenTtl;

    /** ISO-8601 duration string (e.g. PT1H). */
    private String refreshTokenTtl;

    /** ISO-8601 duration string (e.g. PT5M). */
    private String authorizationCodeTtl;

    private Boolean reuseRefreshTokens;
}
