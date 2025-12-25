package com.bootsandcats.oauth2.k8s;

import lombok.Data;

@Data
public class OAuth2ClientSecretRef {
    private String name;
    private String key;
}
