package com.bootsandcats.canary.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CanaryController {

    @GetMapping("/")
    public String index(Model model, @AuthenticationPrincipal OidcUser principal) {
        if (principal != null) {
            model.addAttribute("name", principal.getName());
            model.addAttribute("email", principal.getEmail());
            model.addAttribute("attributes", principal.getAttributes());
            model.addAttribute("idToken", principal.getIdToken().getTokenValue());
        }
        return "index";
    }
}
