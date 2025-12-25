package com.bootsandcats.oauth2.k8s;

import java.util.List;

import lombok.Data;

@Data
public class OAuth2ClientStatus {
    private Long observedGeneration;
    private String secretHash;
    private List<String> conditions;
}
