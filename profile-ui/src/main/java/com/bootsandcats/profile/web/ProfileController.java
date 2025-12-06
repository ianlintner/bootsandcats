package com.bootsandcats.profile.web;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProfileController {

    @GetMapping("/")
    public String index(Model model, @AuthenticationPrincipal OidcUser principal) {
        boolean authenticated = principal != null;
        model.addAttribute("authenticated", authenticated);

        if (principal != null) {
            ProfileView profile = buildProfile(principal);
            model.addAttribute("profile", profile);
            model.addAttribute("claims", new TreeMap<>(principal.getClaims()));
            model.addAttribute("idToken", principal.getIdToken().getTokenValue());
            model.addAttribute("authorities", principal.getAuthorities());
        }

        return "index";
    }

    private ProfileView buildProfile(OidcUser principal) {
        Map<String, Object> claims = principal.getClaims();

        String displayName =
                firstNonEmpty(
                        principal.getFullName(),
                        (String) claims.get("name"),
                        (String) claims.get("preferred_username"),
                        principal.getSubject());

        String email = firstNonEmpty(principal.getEmail(), (String) claims.get("email"));
        String issuer = firstNonEmpty((String) claims.get("iss"), "Authorization Server");
        String pictureUrl = (String) claims.get("picture");

        return new ProfileView(displayName, email, principal.getSubject(), issuer, pictureUrl);
    }

    private String firstNonEmpty(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    public record ProfileView(
            String displayName, String email, String subject, String issuer, String pictureUrl) {}
}
