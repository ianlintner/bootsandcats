package com.bootsandcats.oauth2.k8s;

import java.util.List;

import lombok.Data;

@Data
public class OAuth2ClientSpec {
    /** RegisteredClient internal id. */
    private String registeredClientId;

    private String clientId;
    private String clientName;

    /** ISO-8601 instant string. */
    private String clientIdIssuedAt;

    /** ISO-8601 instant string. */
    private String clientSecretExpiresAt;

    private Boolean enabled;
    private Boolean system;
    private String notes;

    /** Pre-encoded client secret. */
    private String encodedSecret;

    /** Optional reference to a Kubernetes Secret containing the encoded secret. */
    private OAuth2ClientSecretRef secretRef;

    private List<String> clientAuthenticationMethods;
    private List<String> authorizationGrantTypes;
    private List<String> redirectUris;
    private List<String> postLogoutRedirectUris;
    private List<String> scopes;

    private OAuth2ClientSettings clientSettings;
    private OAuth2ClientTokenSettings tokenSettings;
}
