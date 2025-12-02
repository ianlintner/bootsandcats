package com.bootsandcats.oauth2.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;

/** Helper utilities for generating EC-based JSON Web Keys. */
public final class JwkSupport {

    private JwkSupport() {
        // Utility class
    }

    /**
     * Generates a new P-256 EC signing key suitable for ES256 tokens.
     *
     * @return ECKey containing both public and private material
     */
    public static ECKey generateEcSigningKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
            ECPrivateKey privateKey = (ECPrivateKey) keyPair.getPrivate();
            return new ECKey.Builder(Curve.P_256, publicKey)
                    .privateKey(privateKey)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.ES256)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to generate EC signing key", ex);
        }
    }
}
