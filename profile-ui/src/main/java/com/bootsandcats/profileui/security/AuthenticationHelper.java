package com.bootsandcats.profileui.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import io.micronaut.security.authentication.Authentication;

/**
 * Helper class for extracting information from the authenticated user's JWT.
 */
public final class AuthenticationHelper {

    private AuthenticationHelper() {
        // Utility class
    }

    /**
     * Get the OAuth2 subject (sub claim) from authentication.
     *
     * @param authentication the authentication object
     * @return the subject identifier
     */
    public static String getSubject(Authentication authentication) {
        // The name in Micronaut Security is typically the subject
        return authentication.getName();
    }

    /**
     * Get the OAuth2 user ID if available in the token claims.
     *
     * @param authentication the authentication object
     * @return the user ID if present
     */
    public static Optional<Long> getUserId(Authentication authentication) {
        Map<String, Object> attributes = authentication.getAttributes();

        // Try different claim names for user ID
        Object userId = attributes.get("user_id");
        if (userId == null) {
            userId = attributes.get("uid");
        }
        if (userId == null) {
            userId = attributes.get("userId");
        }

        if (userId instanceof Number) {
            return Optional.of(((Number) userId).longValue());
        } else if (userId instanceof String) {
            try {
                return Optional.of(Long.parseLong((String) userId));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    /**
     * Get the user's email from token claims.
     *
     * @param authentication the authentication object
     * @return the email if present
     */
    public static Optional<String> getEmail(Authentication authentication) {
        Object email = authentication.getAttributes().get("email");
        return email != null ? Optional.of(email.toString()) : Optional.empty();
    }

    /**
     * Get the user's name from token claims.
     *
     * @param authentication the authentication object
     * @return the name if present
     */
    public static Optional<String> getName(Authentication authentication) {
        Object name = authentication.getAttributes().get("name");
        return name != null ? Optional.of(name.toString()) : Optional.empty();
    }

    /**
     * Check if the user has a specific scope in their token.
     *
     * @param authentication the authentication object
     * @param scope the scope to check
     * @return true if the user has the scope
     */
    public static boolean hasScope(Authentication authentication, String scope) {
        Collection<String> roles = authentication.getRoles();
        if (roles.contains(scope) || roles.contains("SCOPE_" + scope)) {
            return true;
        }

        // Also check attributes for scope claim
        Object scopesClaim = authentication.getAttributes().get("scope");
        if (scopesClaim instanceof String) {
            String[] scopes = ((String) scopesClaim).split(" ");
            for (String s : scopes) {
                if (s.equals(scope)) {
                    return true;
                }
            }
        } else if (scopesClaim instanceof Collection) {
            @SuppressWarnings("unchecked")
            Collection<String> scopesList = (Collection<String>) scopesClaim;
            return scopesList.contains(scope);
        }

        return false;
    }

    /**
     * Check if the user has profile read permission (can read their own profile).
     *
     * @param authentication the authentication object
     * @return true if permitted
     */
    public static boolean canReadProfile(Authentication authentication) {
        return hasScope(authentication, "profile:read") || hasScope(authentication, "profile");
    }

    /**
     * Check if the user has profile write permission (can edit their own profile).
     *
     * @param authentication the authentication object
     * @return true if permitted
     */
    public static boolean canWriteProfile(Authentication authentication) {
        return hasScope(authentication, "profile:write") || hasScope(authentication, "profile");
    }

    /**
     * Check if the user has admin permission (can manage other profiles).
     *
     * @param authentication the authentication object
     * @return true if admin
     */
    public static boolean isAdmin(Authentication authentication) {
        return hasScope(authentication, "profile:admin")
                || authentication.getRoles().contains("ROLE_ADMIN")
                || authentication.getRoles().contains("admin");
    }

    /**
     * Get all scopes from the authentication.
     *
     * @param authentication the authentication object
     * @return collection of scopes
     */
    @SuppressWarnings("unchecked")
    public static Collection<String> getScopes(Authentication authentication) {
        Object scopesClaim = authentication.getAttributes().get("scope");
        if (scopesClaim instanceof String) {
            return java.util.Arrays.asList(((String) scopesClaim).split(" "));
        } else if (scopesClaim instanceof Collection) {
            return (Collection<String>) scopesClaim;
        }
        return Collections.emptyList();
    }
}
