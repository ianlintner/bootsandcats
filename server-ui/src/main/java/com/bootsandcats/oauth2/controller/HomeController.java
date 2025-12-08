package com.bootsandcats.oauth2.controller;

import java.security.Principal;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Home controller that displays the welcome page with JWT information for authenticated users. */
@Controller
public class HomeController {

    private final ObjectMapper objectMapper;

    public HomeController() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @GetMapping("/")
    public String home(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = principal.getName();
        Map<String, Object> claims = new LinkedHashMap<>();
        String idTokenRaw = null;

        // Extract claims based on authentication type
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
            OAuth2User oauth2User = oauth2Token.getPrincipal();
            claims.putAll(oauth2User.getAttributes());

            // If it's an OIDC user, we can get the ID token
            if (oauth2User instanceof OidcUser oidcUser) {
                if (oidcUser.getIdToken() != null) {
                    idTokenRaw = oidcUser.getIdToken().getTokenValue();
                }
                // Add OIDC-specific claims
                if (oidcUser.getUserInfo() != null) {
                    claims.putAll(oidcUser.getUserInfo().getClaims());
                }
            }
        } else {
            // For form-based login, create basic claims
            claims.put("sub", username);
            claims.put("auth_type", "form");
            claims.put("authorities", authentication.getAuthorities().toString());
        }

        model.addAttribute("username", username);
        model.addAttribute("claims", claims);
        model.addAttribute("claimsJson", formatJson(claims));
        model.addAttribute("idTokenRaw", idTokenRaw);
        model.addAttribute("idTokenDecoded", decodeJwtPayload(idTokenRaw));

        return "home";
    }

    private String formatJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String decodeJwtPayload(String jwt) {
        if (jwt == null || jwt.isEmpty()) {
            return null;
        }
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                // Pretty print the payload
                Object parsed = objectMapper.readValue(payload, Object.class);
                return objectMapper.writeValueAsString(parsed);
            }
        } catch (Exception e) {
            // Return raw payload section if parsing fails
        }
        return null;
    }
}
