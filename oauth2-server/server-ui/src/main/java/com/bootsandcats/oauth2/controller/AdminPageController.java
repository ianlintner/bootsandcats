package com.bootsandcats.oauth2.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Admin console page (backed by /api/admin/** APIs). */
@Controller
public class AdminPageController {

    @GetMapping({"/admin", "/admin/"})
    public String admin() {
        return "admin";
    }
}
