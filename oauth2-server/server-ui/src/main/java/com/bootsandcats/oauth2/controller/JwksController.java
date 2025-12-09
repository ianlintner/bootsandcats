package com.bootsandcats.oauth2.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller to provide backward compatibility for the /.well-known/jwks.json endpoint.
 *
 * <p>Spring Authorization Server exposes JWKS at /oauth2/jwks, but some clients expect the keys at
 * /.well-known/jwks.json. This controller provides a redirect to the standard endpoint.
 */
@Controller
public class JwksController {

    /**
     * Redirects /.well-known/jwks.json to the standard /oauth2/jwks endpoint.
     *
     * @return redirect to /oauth2/jwks
     */
    @GetMapping("/.well-known/jwks.json")
    public String jwksRedirect() {
        return "redirect:/oauth2/jwks";
    }
}
