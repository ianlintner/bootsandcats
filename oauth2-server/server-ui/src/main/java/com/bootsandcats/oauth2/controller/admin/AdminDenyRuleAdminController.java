package com.bootsandcats.oauth2.controller.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bootsandcats.oauth2.dto.admin.AdminDenyRuleSummary;
import com.bootsandcats.oauth2.dto.admin.AdminDenyRuleUpsertRequest;
import com.bootsandcats.oauth2.service.admin.AdminDenyRuleService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/deny-rules")
@Validated
public class AdminDenyRuleAdminController {

    private final AdminDenyRuleService adminDenyRuleService;

    public AdminDenyRuleAdminController(AdminDenyRuleService adminDenyRuleService) {
        this.adminDenyRuleService = adminDenyRuleService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminDenyRuleSummary>> listRules() {
        return ResponseEntity.ok(adminDenyRuleService.listRules());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminDenyRuleSummary> createRule(
            @Valid @RequestBody AdminDenyRuleUpsertRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                adminDenyRuleService.createRule(request, authentication.getName(), httpRequest));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminDenyRuleSummary> updateRule(
            @PathVariable long id,
            @Valid @RequestBody AdminDenyRuleUpsertRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                adminDenyRuleService.updateRule(
                        id, request, authentication.getName(), httpRequest));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRule(
            @PathVariable long id, Authentication authentication, HttpServletRequest httpRequest) {
        adminDenyRuleService.deleteRule(id, authentication.getName(), httpRequest);
        return ResponseEntity.noContent().build();
    }
}
