package com.bootsandcats.oauth2.log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utilities for masking and fingerprinting potentially sensitive values.
 *
 * <p>These helpers are intended for diagnostic logging. They MUST NOT be used to transform values
 * for authentication/authorization decisions.
 */
public final class MaskingUtils {

    private MaskingUtils() {}

    /**
     * Masks a value by keeping the first {@code keepFirst} characters and the last {@code keepLast}
     * characters, and replacing the middle with an ellipsis.
     *
     * <p>Guarantee: for any non-empty input, at least one character is masked.
     */
    public static String maskKeepEnds(String value, int keepFirst, int keepLast) {
        if (value == null) {
            return null;
        }
        int len = value.length();
        if (len == 0) {
            return "";
        }

        int safeKeepFirst = Math.max(0, keepFirst);
        int safeKeepLast = Math.max(0, keepLast);

        // Ensure we never reveal the full value.
        // If keepFirst + keepLast >= len, reduce keeps so at least one character is masked.
        if (safeKeepFirst + safeKeepLast >= len) {
            // Leave at least 1 character for the masked portion.
            int maxReveal = len - 1;
            if (maxReveal <= 0) {
                return "***";
            }
            safeKeepFirst = Math.min(safeKeepFirst, maxReveal);
            safeKeepLast = Math.min(safeKeepLast, maxReveal - safeKeepFirst);
        }

        String prefix = safeKeepFirst > 0 ? value.substring(0, safeKeepFirst) : "";
        String suffix = safeKeepLast > 0 ? value.substring(len - safeKeepLast) : "";

        if (prefix.isEmpty() && suffix.isEmpty()) {
            return "***" + "(len=" + len + ")";
        }

        return prefix + "…" + suffix + "(len=" + len + ")";
    }

    /** Masks a value keeping only the last {@code keepLast} characters. */
    public static String maskKeepLast(String value, int keepLast) {
        return maskKeepEnds(value, 0, keepLast);
    }

    /** Masks a value keeping only the first {@code keepFirst} characters. */
    public static String maskKeepFirst(String value, int keepFirst) {
        return maskKeepEnds(value, keepFirst, 0);
    }

    /**
     * Returns a SHA-256 hex fingerprint of the input.
     *
     * <p>This is safe to log and is useful for comparing whether two values are identical without
     * revealing the value.
     */
    public static String sha256Hex(String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JRE; if this happens, something is fundamentally wrong.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
