package com.bootsandcats.oauth2.k8s;

import lombok.Data;

@Data
public class OAuth2ClientSettings {
    private Boolean requireProofKey;
    private Boolean requireAuthorizationConsent;
}
