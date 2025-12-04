package com.bootsandcats.oauth2.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Controller to handle documentation directory redirects.
 * MkDocs generates index.html files in subdirectories, but Spring Boot's
 * static resource handler doesn't automatically append index.html to directory paths.
 * This controller handles directory paths by redirecting to the explicit index.html file.
 */
@Controller
public class DocsController {

    /**
     * Redirect /docs to /docs/index.html
     */
    @GetMapping("/docs")
    public RedirectView redirectDocsRoot() {
        return new RedirectView("/docs/index.html");
    }

    /**
     * Handle directory paths under /docs/ by redirecting to index.html.
     * Examples: /docs/architecture/ -> /docs/architecture/index.html
     */
    @GetMapping("/docs/*/")
    public RedirectView redirectDocsSubdir(HttpServletRequest request) {
        String path = request.getRequestURI();
        return new RedirectView(path + "index.html");
    }

    /**
     * Handle deeper nested directory paths.
     * Examples: /docs/api/oauth2-endpoints/ -> /docs/api/oauth2-endpoints/index.html
     */
    @GetMapping("/docs/*/*/")
    public RedirectView redirectDocsDeepSubdir(HttpServletRequest request) {
        String path = request.getRequestURI();
        return new RedirectView(path + "index.html");
    }
}



