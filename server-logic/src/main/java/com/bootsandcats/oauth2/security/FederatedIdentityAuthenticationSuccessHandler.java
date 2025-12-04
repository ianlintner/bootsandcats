package com.bootsandcats.oauth2.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import com.bootsandcats.oauth2.model.User;
import com.bootsandcats.oauth2.repository.UserRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles successful federated identity (GitHub, Google, etc.) authentication. After federated
 * login completes, this handler: 1. Creates or updates the user record in the database 2. Redirects
 * back to the original saved request (typically /oauth2/authorize for the OAuth2 flow)
 */
@Component
public class FederatedIdentityAuthenticationSuccessHandler
        extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public FederatedIdentityAuthenticationSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
        // Ensure we use the session-based request cache
        setRequestCache(requestCache);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws ServletException, IOException {
        if (authentication instanceof OAuth2AuthenticationToken) {
            OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
            OAuth2User oauthUser = oauthToken.getPrincipal();
            String provider = oauthToken.getAuthorizedClientRegistrationId();
            String providerId = oauthUser.getName();

            // Different providers might use different attributes for ID/Email
            // GitHub uses 'id' (integer) as name, but we might want login
            // Google uses 'sub'

            String email = oauthUser.getAttribute("email");
            String name = oauthUser.getAttribute("name");
            String picture = oauthUser.getAttribute("avatar_url"); // GitHub
            if (picture == null) {
                picture = oauthUser.getAttribute("picture"); // Google
            }

            String username = oauthUser.getAttribute("login"); // GitHub
            if (username == null) {
                username = email; // Fallback
            }

            User user =
                    userRepository
                            .findByProviderAndProviderId(provider, providerId)
                            .orElseGet(
                                    () -> {
                                        User newUser = new User();
                                        newUser.setProvider(provider);
                                        newUser.setProviderId(providerId);
                                        return newUser;
                                    });

            user.setEmail(email);
            user.setName(name);
            user.setPictureUrl(picture);
            user.setUsername(username);
            user.setLastLogin(Instant.now());

            userRepository.save(user);
        }

        // Check if there's a saved OAuth2 authorization request to continue
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            String savedUrl = savedRequest.getRedirectUrl();
            // If the saved request was an OAuth2 authorization request, redirect to it
            if (savedUrl.contains("/oauth2/authorize")) {
                getRedirectStrategy().sendRedirect(request, response, savedUrl);
                return;
            }
        }

        // Otherwise, use default behavior (redirect to default target or saved request)
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
