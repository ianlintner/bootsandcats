package com.bootsandcats.oauth2.tools;

import com.bootsandcats.oauth2.crypto.JwkSupport;
import com.nimbusds.jose.jwk.JWKSet;

/**
 * Simple CLI utility that prints a newly generated EC JWK Set (including private key material).
 */
public final class EcJwkGenerator {

    private EcJwkGenerator() {
        // Prevent instantiation
    }

    public static void main(String[] args) {
        JWKSet jwkSet = new JWKSet(JwkSupport.generateEcSigningKey());
        System.out.println(jwkSet.toJSONObject(true).toJSONString());
    }
}
