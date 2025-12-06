package com.bootsandcats.oauth2.controller;

import java.util.Locale;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

/** Renders the custom themed login page used by the Authorization Server. */
@Controller
public class LoginController {

    private final Environment environment;

    public LoginController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute(
                "githubEnabled",
                isConfigured("spring.security.oauth2.client.registration.github.client-id"));
        model.addAttribute(
                "googleEnabled",
                isConfigured("spring.security.oauth2.client.registration.google.client-id"));
        model.addAttribute(
                "azureEnabled",
                isConfigured("spring.security.oauth2.client.registration.azure.client-id"));
        return "login";
    }

    private boolean isConfigured(String propertyKey) {
        String value = environment.getProperty(propertyKey);
        if (!StringUtils.hasText(value)) {
            return false;
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        return !normalized.contains("placeholder") && !normalized.startsWith("http");
    }
}
