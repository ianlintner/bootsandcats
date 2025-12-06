package com.bootsandcats.profile.web;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.bootsandcats.oauth2.client.OAuth2ServerHealth;
import com.bootsandcats.profile.service.OAuth2ServerClientService;

@Controller
public class ProfileController {

    private final OAuth2ServerClientService serverClientService;

    public ProfileController(OAuth2ServerClientService serverClientService) {
        this.serverClientService = serverClientService;
    }

    @GetMapping("/")
    public String index(
            Model model,
            @AuthenticationPrincipal OidcUser principal,
            @RegisteredOAuth2AuthorizedClient("oauth2-server")
                    OAuth2AuthorizedClient authorizedClient) {
        boolean authenticated = principal != null;
        model.addAttribute("authenticated", authenticated);
        model.addAttribute("userInfo", Map.of());

        OAuth2ServerHealth health = serverClientService.fetchHealth().orElse(null);
        model.addAttribute("serverHealth", health);

        if (principal != null) {
            ProfileView profile = buildProfile(principal);
            model.addAttribute("profile", profile);
            model.addAttribute("claims", new TreeMap<>(principal.getClaims()));
            model.addAttribute("idToken", principal.getIdToken().getTokenValue());
            model.addAttribute("authorities", principal.getAuthorities());

            Map<String, Object> userInfo =
                    serverClientService
                            .fetchUserInfo(getAccessToken(authorizedClient))
                            .orElseGet(() -> Map.of());
            model.addAttribute("userInfo", userInfo);
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

    private String getAccessToken(OAuth2AuthorizedClient authorizedClient) {
        return Optional.ofNullable(authorizedClient)
                .map(OAuth2AuthorizedClient::getAccessToken)
                .map(token -> token.getTokenValue())
                .orElse(null);
    }

    public record ProfileView(
            String displayName, String email, String subject, String issuer, String pictureUrl) {}
}
